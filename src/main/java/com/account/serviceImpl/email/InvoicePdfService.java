package com.account.serviceImpl.email;

import com.account.domain.Contact;
import com.account.domain.Organization;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.invoice.Invoice;
import com.account.domain.invoice.InvoiceLineItem;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.InvoiceRepository;
import com.account.repository.OrganizationRepository;
import com.account.serviceImpl.pdf.HtmlPdfRenderer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.account.serviceImpl.email.EstimateEmailComposer.firstNonBlank;
import static com.account.serviceImpl.email.EstimateEmailComposer.joinNonBlank;
import static com.account.serviceImpl.email.EstimateEmailComposer.money;
import static com.account.serviceImpl.email.EstimateEmailComposer.trimToNull;
import static com.account.serviceImpl.email.InvoiceDocumentSupport.*;

/**
 * Generates Invoice_<no>.pdf, a GST tax invoice, from
 * templates/invoice-pdf-template.html.
 *
 * Contains: seller GSTIN/PAN, buyer GSTIN, place of supply, SAC per line,
 * CGST/SGST or IGST breakup, total in words, IRN + Ack + signed QR code
 * (when the e-invoice is confirmed) and bank details from the central
 * Organization record.
 *
 * Seller details come from the invoice's organization snapshot, so an old
 * invoice keeps the details it was issued with.
 *
 * Call inside a transaction (reads lazy associations).
 */
@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private static final Logger log = LogManager.getLogger(InvoicePdfService.class);

    public static final String TEMPLATE_NAME = "invoice-pdf-template";

    private final ITemplateEngine templateEngine;
    private final HtmlPdfRenderer htmlPdfRenderer;
    private final OrganizationRepository organizationRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceEmailComposer invoiceEmailComposer;

    // =====================================================
    // PUBLIC API
    // =====================================================

    public byte[] generate(Invoice invoice) {
        Objects.requireNonNull(invoice, "invoice is required");

        Organization org = organizationRepository.findTopOrganization().orElse(null);

        Context context = new Context(Locale.ENGLISH);
        context.setVariables(buildVariables(invoice, org));
        String html = templateEngine.process(TEMPLATE_NAME, context);

        byte[] pdf = htmlPdfRenderer.render(html);
        log.info("Invoice PDF generated | invoice={} | bytes={}", invoice.getInvoiceNumber(), pdf.length);
        return pdf;
    }

    /** Public link: /public/invoices/{uuid}/pdf */
    @Transactional(readOnly = true)
    public PdfFile generateByPublicUuid(String publicUuid) {
        Invoice invoice = invoiceRepository.findByPublicUuidAndIsCancelledFalse(publicUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found", "INVOICE_NOT_FOUND"));
        return new PdfFile(fileName(invoice), generate(invoice));
    }

    /** Internal preview. */
    @Transactional(readOnly = true)
    public PdfFile generateById(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice not found with id: " + invoiceId, "INVOICE_NOT_FOUND"));
        return new PdfFile(fileName(invoice), generate(invoice));
    }

    // =====================================================
    // TEMPLATE VARIABLES
    // =====================================================

    private Map<String, Object> buildVariables(Invoice invoice, Organization org) {
        Map<String, Object> v = new LinkedHashMap<>();
        String currency = invoice.getCurrency();
        GstRegistrationType gstType = gstType(invoice);
        boolean zeroRated = invoice.isZeroRatedSupply();

        // ---------- Seller (invoice snapshot, org as fallback) ----------
        v.put("orgName", firstNonBlank(invoice.getOrganizationName(), org != null ? org.getName() : "Corpseed"));
        String logo = firstNonBlank(invoice.getOrganizationLogoUrl(), org != null ? org.getLogoUrl() : null);
        v.put("orgLogoUrl", logo != null && logo.matches("(?i)^https?://.*") ? logo : null);
        v.put("orgAddress", joinNonBlank(", ",
                invoice.getOrganizationAddressLine1(), invoice.getOrganizationAddressLine2(),
                invoice.getOrganizationCity(), invoice.getOrganizationState(),
                invoice.getOrganizationPinCode(), invoice.getOrganizationCountry()));
        v.put("orgGstNo", trimToNull(invoice.getOrganizationGstNo()));
        v.put("orgPanNo", trimToNull(invoice.getOrganizationPanNo()));
        v.put("orgCin", trimToNull(invoice.getOrganizationCinNumber()));
        v.put("orgContactLine", joinNonBlank(" | ",
                invoice.getOrganizationPhone(), invoice.getOrganizationEmail(), invoice.getOrganizationWebsite()));

        // ---------- Invoice ----------
        v.put("title", "Tax Invoice");
        v.put("invoiceNumber", invoice.getInvoiceNumber());
        v.put("issueDate", formatDate(invoice.getInvoiceDate()));
        v.put("placeOfSupply", placeOfSupply(invoice));
        v.put("serviceName", firstNonBlank(invoice.getSolutionName(), "-"));
        v.put("supplyNote", !zeroRated ? null
                : gstType == GstRegistrationType.INTERNATIONAL
                ? "Export of services – zero-rated supply, no GST charged."
                : "Supply to SEZ – zero-rated supply, no GST charged.");

        // ---------- E-invoice ----------
        v.put("irn", trimToNull(invoice.getEInvoiceIrn()));
        v.put("ackNo", trimToNull(invoice.getEInvoiceAckNo()));
        v.put("ackDate", formatDate(invoice.getEInvoiceAckDate()));
        v.put("qrCode", qrCodeDataUri(invoice.getSignedQrCode(), 260));

        // ---------- Buyer ----------
        Company company = company(invoice);
        CompanyUnit unit = unit(invoice);
        Contact contact = contact(invoice);
        v.put("buyerName", company != null ? trimToNull(company.getName()) : null);
        v.put("buyerUnit", unit != null ? trimToNull(unit.getUnitName()) : null);
        v.put("buyerAddress", unit != null ? joinNonBlank(", ",
                unit.getAddressLine1(), unit.getAddressLine2(), unit.getCity(),
                unit.getState(), unit.getPinCode(), unit.getCountry()) : null);
        v.put("buyerGstin", firstNonBlank(invoice.getBuyerGstin(), unit != null ? unit.getGstNo() : null));
        v.put("buyerContact", contact != null ? joinNonBlank(" | ",
                contact.getName(), contact.getContactNo(), cleanEmails(contact.getEmails())) : null);

        // ---------- Lines ----------
        boolean igst = invoice.getIgstAmount() != null && invoice.getIgstAmount().signum() > 0;
        v.put("lineItems", buildLines(invoice, currency));

        // ---------- Totals ----------
        List<AmountRow> taxRows = new ArrayList<>();
        if (zeroRated) {
            taxRows.add(new AmountRow("IGST (zero-rated)", money(BigDecimal.ZERO, currency, true)));
        } else if (igst) {
            taxRows.add(new AmountRow("IGST", money(invoice.getIgstAmount(), currency, true)));
        } else {
            taxRows.add(new AmountRow("CGST", money(invoice.getCgstAmount(), currency, true)));
            taxRows.add(new AmountRow("SGST", money(invoice.getSgstAmount(), currency, true)));
        }
        BigDecimal roundOff = invoice.getRoundOffAmount();
        if (roundOff != null && roundOff.setScale(2, RoundingMode.HALF_UP).signum() != 0) {
            taxRows.add(new AmountRow("Round off", money(roundOff, currency, true)));
        }
        v.put("subTotal", money(invoice.getSubTotalExGst(), currency, true));
        v.put("taxRows", taxRows);
        v.put("grandTotal", money(invoice.getGrandTotal(), currency, true));
        v.put("amountInWords", amountInWords(invoice.getGrandTotal(), currency));

        // ---------- Payment status ----------
        boolean paid = isPaid(invoice);
        v.put("paid", paid);
        v.put("amountDue", amount(amountDue(invoice), currency));
        v.put("dueDate", formatDate(dueDate(invoice, invoiceEmailComposer.getPaymentDueDays())));

        // ---------- Bank (central Organization record) ----------
        boolean bank = org != null && org.isBankAccountPresent() && trimToNull(org.getAccountNo()) != null;
        v.put("bankPresent", bank);
        if (bank) {
            v.put("bankAccountName", trimToNull(org.getAccountHolderName()));
            v.put("bankName", joinNonBlank(", ", org.getBankName(), org.getBranch()));
            v.put("bankAccountNo", trimToNull(org.getAccountNo()));
            v.put("bankIfsc", trimToNull(org.getIfscCode()));
            v.put("bankSwift", gstType == GstRegistrationType.INTERNATIONAL ? trimToNull(org.getSwiftCode()) : null);
            v.put("bankUpi", trimToNull(org.getUpiId()));
        }
        return v;
    }

    private List<PdfLine> buildLines(Invoice invoice, String currency) {
        if (invoice.getLineItems() == null) {
            return List.of();
        }
        List<PdfLine> rows = new ArrayList<>();
        int index = 1;
        for (InvoiceLineItem li : invoice.getLineItems()) {   // already ordered by @OrderBy
            if (li == null) {
                continue;
            }
            Object uom = li.getUnit();
            String qty = li.getQuantity() != null ? String.valueOf(li.getQuantity()) : "1";
            String unitText = uom != null ? trimToNull(String.valueOf(uom)) : null;

            rows.add(new PdfLine(
                    index++,
                    firstNonBlank(li.getItemName(), "Item"),
                    trimToNull(li.getDescription()),
                    firstNonBlank(li.getHsnSacCode(), "-"),
                    unitText != null ? qty + " " + unitText : qty,
                    money(li.getUnitPriceExGst(), currency, true),
                    money(li.getLineTotalExGst(), currency, true),
                    li.getGstRate() == null ? "0%" : li.getGstRate().stripTrailingZeros().toPlainString() + "%",
                    money(li.getGstAmount(), currency, true),
                    money(li.getLineTotalWithGst(), currency, true)
            ));
        }
        return rows;
    }

    private static String cleanEmails(String raw) {
        if (raw == null) {
            return null;
        }
        return trimToNull(raw.replaceAll("[\\[\\]\"]", " ").trim().replaceAll("\\s*[,;\\s]\\s*", ", "));
    }

    // =====================================================
    // VIEW MODELS
    // =====================================================

    @Getter
    @AllArgsConstructor
    public static class PdfLine {
        private final int index;
        private final String name;
        private final String description;
        private final String sac;
        private final String quantity;
        private final String rate;
        private final String taxable;
        private final String gstRate;
        private final String gstAmount;
        private final String total;
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
