package com.account.serviceImpl.pdf;

import com.account.domain.Contact;
import com.account.domain.Organization;
import com.account.domain.company.CompanyUnit;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.EstimateRepository;
import com.account.repository.OrganizationRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.util.*;

import static com.account.serviceImpl.email.EstimateEmailComposer.*;

/**
 * Generates Estimate_<no>.pdf from templates/estimate-pdf-template.html
 * (Thymeleaf -> HTML -> PDF with OpenHTMLtoPDF).
 *
 * Fonts: put NotoSans-Regular.ttf and NotoSans-Bold.ttf in
 * src/main/resources/fonts/ so the ₹ symbol renders. Without them the PDF
 * still generates, but "₹" is printed as "Rs.".
 *
 * Must be called inside a transaction (reads lazy associations).
 */
@Service
@RequiredArgsConstructor
public class EstimatePdfService {

    private static final Logger log = LogManager.getLogger(EstimatePdfService.class);

    public static final String TEMPLATE_NAME = "estimate-pdf-template";

    private static final String FONT_FAMILY = "Noto Sans";
    private static final String FONT_REGULAR = "/fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/NotoSans-Bold.ttf";

    private final ITemplateEngine templateEngine;
    private final OrganizationRepository organizationRepository;
    private final EstimateRepository estimateRepository;

    @Value("${app.mail.brand-name:Corpseed}")
    private String brandName;

    // =====================================================
    // PUBLIC API
    // =====================================================

    public byte[] generate(Estimate estimate) {
        Objects.requireNonNull(estimate, "estimate is required");

        Organization org = organizationRepository.findTopOrganization().orElse(null);

        Context context = new Context(Locale.ENGLISH);
        context.setVariables(buildVariables(estimate, org));
        String html = templateEngine.process(TEMPLATE_NAME, context);

        boolean fontsAvailable = resourceExists(FONT_REGULAR) && resourceExists(FONT_BOLD);
        if (!fontsAvailable) {
            log.warn("PDF fonts missing ({} / {}); printing '₹' as 'Rs.'", FONT_REGULAR, FONT_BOLD);
            html = html.replace("₹", "Rs. ");
        }

        // Jsoup turns Thymeleaf's HTML5 output into a DOM the PDF renderer accepts
        org.w3c.dom.Document document = new W3CDom().fromJsoup(Jsoup.parse(html));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            if (fontsAvailable) {
                builder.useFont(() -> getClass().getResourceAsStream(FONT_REGULAR),
                        FONT_FAMILY, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
                builder.useFont(() -> getClass().getResourceAsStream(FONT_BOLD),
                        FONT_FAMILY, 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            }

            builder.withW3cDocument(document, baseUri());
            builder.toStream(out);
            builder.run();

            byte[] pdf = out.toByteArray();
            log.info("Estimate PDF generated | estimate={} | bytes={}", estimate.getEstimateNumber(), pdf.length);
            return pdf;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate PDF for estimate " + estimate.getEstimateNumber(), e);
        }
    }

    public static String fileName(Estimate estimate) {
        return "Estimate_" + estimate.getEstimateNumber() + ".pdf";
    }

    /** Used by the public link: /public/estimates/{uuid}/pdf */
    @Transactional(readOnly = true)
    public PdfFile generateByPublicUuid(String publicUuid) {
        Estimate estimate = estimateRepository.findByPublicUuidAndIsDeletedFalse(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Estimate not found", "ESTIMATE_NOT_FOUND"));
        return new PdfFile(fileName(estimate), generate(estimate));
    }

    /** Used by the internal preview endpoint. */
    @Transactional(readOnly = true)
    public PdfFile generateById(Long estimateId) {
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with id: " + estimateId, "ESTIMATE_NOT_FOUND"));
        return new PdfFile(fileName(estimate), generate(estimate));
    }

    // =====================================================
    // TEMPLATE VARIABLES
    // =====================================================

    private Map<String, Object> buildVariables(Estimate estimate, Organization org) {
        Map<String, Object> v = new LinkedHashMap<>();
        String currency = estimate.getCurrency();
        boolean zeroRated = estimate.isZeroRatedSupply();

        // ---------- Organization (seller) ----------
        v.put("orgName", org != null ? firstNonBlank(org.getName(), brandName) : brandName);
        v.put("orgLogoUrl", org != null ? httpUrlOrNull(org.getLogoUrl()) : null);
        v.put("orgAddress", org != null ? joinNonBlank(", ",
                org.getAddressLine1(), org.getAddressLine2(), org.getCity(),
                org.getState(), org.getPinCode()) : null);
        v.put("orgGstNo", org != null ? trimToNull(org.getGstNo()) : null);
        v.put("orgPanNo", org != null ? trimToNull(org.getPanNo()) : null);
        v.put("orgContactLine", org != null ? joinNonBlank(" | ",
                org.getPhone(), org.getEmail(), org.getWebsite()) : null);

        // ---------- Estimate ----------
        v.put("estimateNumber", estimate.getEstimateNumber());
        v.put("issueDate", formatDate(estimate.getEstimateDate()));
        v.put("validUntil", formatDate(estimate.getValidUntil()));
        v.put("clientPoNumber", trimToNull(estimate.getClientPoNumber()));
        v.put("paymentTerm", trimToNull(estimate.getPaymentTerm()));
        v.put("serviceName", firstNonBlank(estimate.getSolutionName(), "-"));
        v.put("customerNotes", trimToNull(estimate.getCustomerNotes()));

        // ---------- Bill to ----------
        CompanyUnit unit = estimate.getUnit();
        Contact contact = resolveContact(estimate);
        v.put("billToCompany", estimate.getCompany() != null ? trimToNull(estimate.getCompany().getName()) : null);
        v.put("billToUnit", unit != null ? trimToNull(unit.getUnitName()) : null);
        v.put("billToAddress", unit != null ? joinNonBlank(", ",
                unit.getAddressLine1(), unit.getAddressLine2(), unit.getCity(),
                unit.getState(), unit.getPinCode()) : null);
        v.put("billToGstNo", unit != null ? trimToNull(unit.getGstNo()) : null);
        v.put("billToContact", contact != null ? joinNonBlank(" | ",
                contact.getName(), contact.getContactNo(), cleanEmails(contact.getEmails())) : null);

        // ---------- Amounts ----------
        v.put("lineItems", buildLineRows(estimate, currency));
        v.put("subTotal", money(estimate.getSubTotalExGst(), currency, true));
        v.put("taxRows", buildTaxRows(estimate, currency, zeroRated));
        v.put("grandTotal", money(estimate.getGrandTotal(), currency, true));
        v.put("amountTotalNote", zeroRated ? "Zero-rated supply, no GST charged" : "Inclusive of GST");

        // ---------- Terms & bank ----------
        v.put("terms", toLines(org != null ? org.getEstimateConditions() : null));
        boolean bank = org != null && org.isBankAccountPresent() && trimToNull(org.getAccountNo()) != null;
        v.put("bankPresent", bank);
        if (bank) {
            v.put("bankAccountName", trimToNull(org.getAccountHolderName()));
            v.put("bankName", joinNonBlank(", ", org.getBankName(), org.getBranch()));
            v.put("bankAccountNo", trimToNull(org.getAccountNo()));
            v.put("bankIfsc", trimToNull(org.getIfscCode()));
            v.put("bankUpi", trimToNull(org.getUpiId()));
        }
        return v;
    }

    private List<PdfLine> buildLineRows(Estimate estimate, String currency) {
        if (estimate.getLineItems() == null) {
            return List.of();
        }
        List<EstimateLineItem> items = estimate.getLineItems().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(EstimateLineItem::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<PdfLine> rows = new ArrayList<>();
        int index = 1;
        for (EstimateLineItem item : items) {
            Object unitOfMeasure = item.getUnit();
            rows.add(new PdfLine(
                    index++,
                    firstNonBlank(item.getItemName(), "Item"),
                    trimToNull(item.getDescription()),
                    trimToNull(item.getHsnSacCode()),
                    item.getQuantity() != null ? String.valueOf(item.getQuantity()) : "1",
                    unitOfMeasure != null ? trimToNull(String.valueOf(unitOfMeasure)) : null,
                    money(item.getUnitPriceExGst(), currency, true),
                    percent(item.getGstRate()),
                    money(item.getLineTotalExGst(), currency, true)
            ));
        }
        return rows;
    }

    private List<AmountRow> buildTaxRows(Estimate estimate, String currency, boolean zeroRated) {
        List<AmountRow> rows = new ArrayList<>();
        if (zeroRated) {
            rows.add(new AmountRow(estimate.isSezSupply()
                    ? "GST (zero-rated SEZ supply)"
                    : "GST (zero-rated export of services)", money(BigDecimal.ZERO, currency, true)));
        } else {
            if (isPositive(estimate.getCgstAmount())) {
                rows.add(new AmountRow("CGST", money(estimate.getCgstAmount(), currency, true)));
            }
            if (isPositive(estimate.getSgstAmount())) {
                rows.add(new AmountRow("SGST", money(estimate.getSgstAmount(), currency, true)));
            }
            if (isPositive(estimate.getIgstAmount())) {
                rows.add(new AmountRow("IGST", money(estimate.getIgstAmount(), currency, true)));
            }
            if (rows.isEmpty()) {
                rows.add(new AmountRow("GST", money(estimate.getTotalGstAmount(), currency, true)));
            }
        }
        BigDecimal roundOff = estimate.getRoundOffAmount();
        if (roundOff != null && roundOff.setScale(2, RoundingMode.HALF_UP).signum() != 0) {
            rows.add(new AmountRow("Round off", money(roundOff, currency, true)));
        }
        return rows;
    }

    // =====================================================
    // HELPERS
    // =====================================================

    /** estimateConditions may be a String (one per line) or a collection. */
    private static List<String> toLines(Object conditions) {
        if (conditions == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        if (conditions instanceof Collection<?> c) {
            c.forEach(o -> { if (o != null) lines.add(String.valueOf(o)); });
        } else {
            lines.addAll(Arrays.asList(String.valueOf(conditions).split("\\r?\\n")));
        }
        return lines.stream()
                .map(s -> s.replaceFirst("^\\s*(\\d+[.)]|[-•*])\\s*", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String cleanEmails(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[\\[\\]\"]", " ").trim().replaceAll("\\s*[,;\\s]\\s*", ", ");
        return trimToNull(cleaned);
    }

    private static String httpUrlOrNull(String url) {
        String u = trimToNull(url);
        return u != null && u.matches("(?i)^https?://.*") ? u : null;
    }

    private static String percent(BigDecimal rate) {
        return rate == null ? "0%" : rate.stripTrailingZeros().toPlainString() + "%";
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean resourceExists(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String baseUri() {
        URL root = getClass().getResource("/");
        return root != null ? root.toExternalForm() : "file:/";
    }

    // =====================================================
    // VIEW MODELS (getters so Thymeleaf can read item.name etc.)
    // =====================================================

    @Getter
    @AllArgsConstructor
    public static class PdfLine {
        private final int index;
        private final String name;
        private final String description;
        private final String hsnSac;
        private final String quantity;
        private final String unit;
        private final String rate;
        private final String gstRate;
        private final String amount;
    }

    @Getter
    @AllArgsConstructor
    public static class AmountRow {
        private final String label;
        private final String amount;
    }

    public record PdfFile(String fileName, byte[] content) {
    }
}
