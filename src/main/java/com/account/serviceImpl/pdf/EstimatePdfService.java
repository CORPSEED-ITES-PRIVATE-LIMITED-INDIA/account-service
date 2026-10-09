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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.account.serviceImpl.email.EstimateEmailComposer.*;

/**
 * Generates Estimate_<no>.pdf from templates/estimate-pdf-template.html
 * (Thymeleaf -> HTML -> PDF with OpenHTMLtoPDF).
 *
 * Layout: header (logo, company, CIN/GST/PAN, address, email, phone | Estimate no.,
 * client PO, dates), Bill to / Ship to, items table with CGST/SGST (or IGST) rows
 * and grand total, amount in words, HSN-wise tax details, bank box, note, terms.
 *
 * Fonts: put NotoSans-Regular.ttf and NotoSans-Bold.ttf in
 * src/main/resources/fonts/ so the ₹ symbol renders. Without them "₹" prints as "Rs.".
 *
 * Must be called inside a transaction (reads lazy associations).
 */
@Service
@RequiredArgsConstructor
public class EstimatePdfService {

    private static final Logger log = LogManager.getLogger(EstimatePdfService.class);

    public static final String TEMPLATE_NAME = "estimate-pdf-template";

    private static final DateTimeFormatter PDF_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

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
            html = html.replace("₹ ", "Rs. ").replace("₹", "Rs.");
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
        boolean interState = zeroRated || isPositive(estimate.getIgstAmount());

        // ---------- Organization (seller) ----------
        v.put("orgName", org != null ? firstNonBlank(org.getName(), brandName) : brandName);
        v.put("orgLogoUrl", org != null ? httpUrlOrNull(org.getLogoUrl()) : null);
        v.put("orgCin", prop(org, "getCinNumber", "getCin", "getCinNo"));
        v.put("orgGstNo", org != null ? trimToNull(org.getGstNo()) : null);
        v.put("orgPanNo", org != null ? trimToNull(org.getPanNo()) : null);
        v.put("orgAddress", org != null ? joinNonBlank(", ",
                org.getAddressLine1(), org.getAddressLine2(), org.getCity(),
                org.getState(), prop(org, "getCountry"), org.getPinCode()) : null);
        v.put("orgEmail", org != null ? trimToNull(org.getEmail()) : null);
        v.put("orgPhone", org != null ? trimToNull(org.getPhone()) : null);

        // ---------- Estimate ----------
        v.put("estimateNumber", estimate.getEstimateNumber());
        v.put("clientPoNumber", trimToNull(estimate.getClientPoNumber()));
        v.put("issueDate", pdfDate(estimate.getEstimateDate()));
        v.put("validUntil", pdfDate(estimate.getValidUntil()));
        v.put("serviceName", firstNonBlank(estimate.getSolutionName(), "-"));
        v.put("customerNotes", trimToNull(estimate.getCustomerNotes()));

        // ---------- Bill to / Ship to (same party unless a separate ship-to exists) ----------
        CompanyUnit unit = estimate.getUnit();
        String partyName = estimate.getCompany() != null ? trimToNull(estimate.getCompany().getName()) : null;
        if (partyName == null && unit != null) {
            partyName = trimToNull(unit.getUnitName());
        }
        String partyGst = unit != null ? trimToNull(unit.getGstNo()) : null;
        String partyAddress = unit != null ? joinNonBlank(", ",
                unit.getAddressLine1(), unit.getAddressLine2(), unit.getCity(),
                unit.getState(), unit.getPinCode()) : null;

        v.put("billToName", partyName);
        v.put("billToGstNo", partyGst);
        v.put("billToAddress", partyAddress);
        v.put("shipToName", partyName);
        v.put("shipToGstNo", partyGst);
        v.put("shipToAddress", partyAddress);

        // ---------- Items + tax rows ----------
        List<EstimateLineItem> items = sortedItems(estimate);
        List<PdfLine> lines = buildLineRows(items, currency);
        v.put("lineItems", lines);
        v.put("taxLines", buildTaxLines(estimate, items, currency, zeroRated, lines.size() + 1));
        v.put("grandTotal", amt(estimate.getGrandTotal(), currency));
        v.put("amountInWords", amountInWords(estimate.getGrandTotal(), currency));

        // ---------- HSN-wise tax details ----------
        v.put("interState", interState);
        buildHsnSummary(items, currency, zeroRated, v);

        // ---------- Bank ----------
        boolean bank = org != null && org.isBankAccountPresent() && trimToNull(org.getAccountNo()) != null;
        v.put("bankPresent", bank);
        if (bank) {
            v.put("bankName", trimToNull(org.getBankName()));
            v.put("bankAccountNo", trimToNull(org.getAccountNo()));
            v.put("bankIfsc", trimToNull(org.getIfscCode()));
        }

        // ---------- Terms ----------
        v.put("terms", toLines(org != null ? org.getEstimateConditions() : null));
        return v;
    }

    private static List<EstimateLineItem> sortedItems(Estimate estimate) {
        if (estimate.getLineItems() == null) {
            return List.of();
        }
        return estimate.getLineItems().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(EstimateLineItem::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private List<PdfLine> buildLineRows(List<EstimateLineItem> items, String currency) {
        List<PdfLine> rows = new ArrayList<>();
        int index = 1;
        for (EstimateLineItem item : items) {
            Object qty = item.getQuantity();
            Object uom = item.getUnit();
            rows.add(new PdfLine(
                    index++,
                    firstNonBlank(item.getItemName(), "Item"),
                    trimToNull(item.getDescription()),
                    firstNonBlank(item.getHsnSacCode(), ""),
                    qty == null ? "1" : plain(qty),
                    amt(item.getUnitPriceExGst(), currency),
                    uom != null ? firstNonBlank(String.valueOf(uom), "Nos") : "Nos",
                    amt(item.getLineTotalExGst(), currency)
            ));
        }
        return rows;
    }

    /** CGST + SGST rows (intra-state) or one IGST row (inter-state / export), plus round off. */
    private List<TaxLine> buildTaxLines(Estimate estimate, List<EstimateLineItem> items, String currency,
                                        boolean zeroRated, int startIndex) {
        List<TaxLine> rows = new ArrayList<>();
        int index = startIndex;
        BigDecimal subTotal = estimate.getSubTotalExGst();
        BigDecimal singleRate = singleGstRate(items);

        if (zeroRated) {
            rows.add(new TaxLine(index++, "IGST (zero-rated)", "0", "%", amt(BigDecimal.ZERO, currency)));
        } else if (isPositive(estimate.getIgstAmount())) {
            rows.add(new TaxLine(index++, "IGST",
                    rateText(singleRate, estimate.getIgstAmount(), subTotal, false), "%",
                    amt(estimate.getIgstAmount(), currency)));
        } else {
            if (isPositive(estimate.getCgstAmount())) {
                rows.add(new TaxLine(index++, "CGST",
                        rateText(singleRate, estimate.getCgstAmount(), subTotal, true), "%",
                        amt(estimate.getCgstAmount(), currency)));
            }
            if (isPositive(estimate.getSgstAmount())) {
                rows.add(new TaxLine(index++, "SGST",
                        rateText(singleRate, estimate.getSgstAmount(), subTotal, true), "%",
                        amt(estimate.getSgstAmount(), currency)));
            }
            if (rows.isEmpty() && isPositive(estimate.getTotalGstAmount())) {
                rows.add(new TaxLine(index++, "GST",
                        rateText(singleRate, estimate.getTotalGstAmount(), subTotal, false), "%",
                        amt(estimate.getTotalGstAmount(), currency)));
            }
        }

        BigDecimal roundOff = estimate.getRoundOffAmount();
        if (roundOff != null && roundOff.setScale(2, RoundingMode.HALF_UP).signum() != 0) {
            rows.add(new TaxLine(index, "Round off", "", "", amt(roundOff, currency)));
        }
        return rows;
    }

    /** One row per HSN/SAC + GST rate, plus a total row. */
    private void buildHsnSummary(List<EstimateLineItem> items, String currency, boolean zeroRated,
                                 Map<String, Object> v) {
        Map<String, BigDecimal[]> groups = new LinkedHashMap<>();   // key -> [taxable, rate]
        for (EstimateLineItem item : items) {
            BigDecimal taxable = nz(item.getLineTotalExGst());
            BigDecimal rate = zeroRated ? BigDecimal.ZERO : nz(item.getGstRate());
            String hsn = firstNonBlank(item.getHsnSacCode(), "-");
            String key = hsn + "|" + rate.stripTrailingZeros().toPlainString();
            BigDecimal[] group = groups.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, rate});
            group[0] = group[0].add(taxable);
        }

        List<HsnRow> rows = new ArrayList<>();
        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalHalf = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal[]> e : groups.entrySet()) {
            String hsn = e.getKey().substring(0, e.getKey().lastIndexOf('|'));
            BigDecimal taxable = e.getValue()[0];
            BigDecimal rate = e.getValue()[1];
            BigDecimal tax = taxable.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            BigDecimal halfRate = rate.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);

            rows.add(new HsnRow(hsn, amt(taxable, currency),
                    pct(rate), pct(halfRate), amt(half, currency), amt(tax, currency)));

            totalTaxable = totalTaxable.add(taxable);
            totalHalf = totalHalf.add(half);
            totalTax = totalTax.add(tax);
        }

        v.put("hsnRows", rows);
        v.put("hsnTotal", new HsnRow("Total", amt(totalTaxable, currency), "-", "-",
                amt(totalHalf, currency), amt(totalTax, currency)));
    }

    // =====================================================
    // HELPERS
    // =====================================================

    /** The GST rate when every line has the same rate, else null. */
    private static BigDecimal singleGstRate(List<EstimateLineItem> items) {
        Set<BigDecimal> rates = new HashSet<>();
        for (EstimateLineItem item : items) {
            if (item.getGstRate() != null) {
                rates.add(item.getGstRate().stripTrailingZeros());
            }
        }
        return rates.size() == 1 ? rates.iterator().next() : null;
    }

    /** "9" for CGST at 18 %; falls back to tax / subtotal when lines have mixed rates. */
    private static String rateText(BigDecimal singleRate, BigDecimal taxAmount, BigDecimal subTotal, boolean half) {
        BigDecimal rate;
        if (singleRate != null) {
            rate = half ? singleRate.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP) : singleRate;
        } else if (subTotal != null && subTotal.signum() > 0 && taxAmount != null) {
            rate = taxAmount.multiply(BigDecimal.valueOf(100)).divide(subTotal, 2, RoundingMode.HALF_UP);
        } else {
            return "";
        }
        return rate.stripTrailingZeros().toPlainString();
    }

    /** ₹ 40,000.00 (space after the symbol, as on the estimate layout). */
    private static String amt(BigDecimal value, String currency) {
        return money(value, currency, true).replaceFirst("^(-?)₹", "$1₹ ");
    }

    private static String pct(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString() + "%";
    }

    private static String plain(Object number) {
        if (number instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(((Number) number).doubleValue()).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(number);
    }

    private static String pdfDate(LocalDate date) {
        return date != null ? date.format(PDF_DATE) : null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /**
     * Reads an optional String property (e.g. CIN, country) without a compile-time
     * dependency on the getter's name. Returns null if no getter exists or it is blank.
     */
    private static String prop(Object target, String... getters) {
        if (target == null) {
            return null;
        }
        for (String getter : getters) {
            try {
                Object value = target.getClass().getMethod(getter).invoke(target);
                String s = value != null ? trimToNull(String.valueOf(value)) : null;
                if (s != null) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
                // getter not present on this entity
            }
        }
        return null;
    }

    // ---------- Amount in words: "Forty Seven Thousand Two Hundred Rupees Only" ----------

    private static final String[] ONES = {
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen",
            "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private static String amountInWords(BigDecimal amount, String currency) {
        BigDecimal v = nz(amount).abs().setScale(2, RoundingMode.HALF_UP);
        long whole = v.longValue();
        int fraction = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        boolean inr = currency == null || "INR".equalsIgnoreCase(currency);
        String major = inr ? "Rupees" : currency.toUpperCase(Locale.ROOT);
        String minor = inr ? "Paise" : "Cents";

        StringBuilder sb = new StringBuilder(words(whole)).append(' ').append(major);
        if (fraction > 0) {
            sb.append(" and ").append(words(fraction)).append(' ').append(minor);
        }
        return sb.append(" Only").toString();
    }

    private static String words(long n) {
        if (n == 0) {
            return "Zero";
        }
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1_000;
        n %= 1_000;
        if (crore > 0) {
            sb.append(words(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(belowHundred((int) lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(belowHundred((int) thousand)).append(" Thousand ");
        }
        if (n > 0) {
            int h = (int) n;
            if (h >= 100) {
                sb.append(ONES[h / 100]).append(" Hundred ");
                h %= 100;
            }
            if (h > 0) {
                sb.append(belowHundred(h));
            }
        }
        return sb.toString().trim();
    }

    private static String belowHundred(int n) {
        if (n < 20) {
            return ONES[n];
        }
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }

    // ---------- Terms / misc ----------

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

    private static String httpUrlOrNull(String url) {
        String u = trimToNull(url);
        return u != null && u.matches("(?i)^https?://.*") ? u : null;
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
        private final String rate;
        private final String per;
        private final String amount;
    }

    @Getter
    @AllArgsConstructor
    public static class TaxLine {
        private final int index;
        private final String label;
        private final String rate;
        private final String per;
        private final String amount;
    }

    @Getter
    @AllArgsConstructor
    public static class HsnRow {
        private final String hsnSac;
        private final String taxable;
        private final String fullRate;
        private final String halfRate;
        private final String halfTax;
        private final String totalTax;
    }

    public record PdfFile(String fileName, byte[] content) {
    }
}
