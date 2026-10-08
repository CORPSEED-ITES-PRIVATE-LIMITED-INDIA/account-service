package com.account.serviceImpl.email;

import com.account.domain.Contact;
import com.account.domain.Organization;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.invoice.Invoice;
import com.account.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.account.serviceImpl.email.EstimateEmailComposer.firstNonBlank;
import static com.account.serviceImpl.email.EstimateEmailComposer.joinNonBlank;
import static com.account.serviceImpl.email.EstimateEmailComposer.trimToNull;
import static com.account.serviceImpl.email.InvoiceDocumentSupport.*;

/**
 * Builds the client-facing invoice email (subject, HTML and plain text) as per
 * the "Invoice email" spec:
 *
 *   Subject: Invoice {{invoice_no}} from Corpseed – {{amount_due}} due by {{due_date}}
 *   Attachment: Invoice_{{invoice_no}}.pdf (GST tax invoice)
 *
 * Paid invoices (payment-first invoices are generated after the payment is
 * approved) use the same layout, but say "Paid" and leave out the
 * pay-online / bank-transfer sections, so the client isn't asked to pay twice.
 *
 * Bank details always come from the central Organization record, never from
 * the invoice, so they can't be altered per invoice.
 *
 * Call inside an open transaction (reads lazy associations).
 */
@Component
@RequiredArgsConstructor
public class InvoiceEmailComposer {

    private static final Logger log = LogManager.getLogger(InvoiceEmailComposer.class);

    public static final String TEMPLATE_NAME = "invoice-email-template";

    private final ITemplateEngine templateEngine;
    private final OrganizationRepository organizationRepository;

    @Value("${app.mail.brand-name:Corpseed}")
    private String brandName;

    @Value("${app.mail.brand-website:www.corpseed.com}")
    private String brandWebsite;

    /** Frontend invoice page, e.g. https://app.corpseed.com/invoice/view (/{uuid} appended) or with {uuid}. */
    @Value("${app.invoice.public-view-url:}")
    private String publicViewUrl;

    /** Backend base URL; used when public-view-url is blank -> {base}/public/invoices/{uuid}/pdf */
    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    /**
     * Pay-online link. Blank = Organization.paymentPageLink.
     * Placeholders: {invoiceNo}, {amount}, {uuid}
     */
    @Value("${app.invoice.payment-link:}")
    private String paymentLinkTemplate;

    /** Due date = invoice date + this many days (unpaid invoices only). */
    @Value("${app.invoice.payment-due-days:7}")
    private int paymentDueDays;

    // =====================================================
    // PUBLIC API
    // =====================================================

    public InvoiceEmail compose(Invoice invoice, boolean hasPdfAttachment) {
        Objects.requireNonNull(invoice, "invoice is required");

        Organization org = organizationRepository.findTopOrganization().orElse(null);
        Map<String, Object> vars = buildVariables(invoice, org, hasPdfAttachment);

        Context context = new Context(Locale.ENGLISH);
        context.setVariables(vars);
        String html = templateEngine.process(TEMPLATE_NAME, context);

        return new InvoiceEmail((String) vars.get("subject"), html, buildPlainText(vars));
    }

    public String buildDocumentLink(String publicUuid) {
        String uuid = trimToNull(publicUuid);
        if (uuid == null) {
            return null;
        }
        String viewUrl = trimToNull(publicViewUrl);
        if (viewUrl != null) {
            return viewUrl.contains("{uuid}")
                    ? viewUrl.replace("{uuid}", uuid)
                    : (viewUrl.endsWith("/") ? viewUrl : viewUrl + "/") + uuid;
        }
        String base = trimToNull(publicBaseUrl);
        if (base != null) {
            return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                    + "/public/invoices/" + uuid + "/pdf";
        }
        log.warn("Invoice link not added: set app.invoice.public-view-url or app.public-base-url");
        return null;
    }

    public int getPaymentDueDays() {
        return paymentDueDays;
    }

    // =====================================================
    // TEMPLATE VARIABLES
    // =====================================================

    private Map<String, Object> buildVariables(Invoice invoice, Organization org, boolean hasPdfAttachment) {
        Map<String, Object> v = new LinkedHashMap<>();
        String currency = invoice.getCurrency();
        String orgName = firstNonBlank(brandName, "Corpseed");
        String invoiceNo = invoice.getInvoiceNumber();
        String serviceName = firstNonBlank(invoice.getSolutionName(), "our services");

        boolean paid = isPaid(invoice);
        BigDecimal due = amountDue(invoice);
        String amountDue = amount(due, currency);
        String amountTotal = amount(invoice.getGrandTotal(), currency);
        String dueDate = formatDate(dueDate(invoice, paymentDueDays));

        // ---------- Brand ----------
        String website = firstNonBlank(brandWebsite, "www.corpseed.com");
        v.put("orgName", orgName);
        v.put("orgWebsite", website.replaceFirst("(?i)^https?://", "").replaceAll("/+$", ""));
        v.put("orgWebsiteHref", website.matches("(?i)^https?://.*") ? website : "https://" + website);
        String orgPhone = org != null ? trimToNull(org.getPhone()) : null;
        v.put("orgPhone", orgPhone);
        v.put("orgPhoneHref", orgPhone != null ? orgPhone.replaceAll("[^+0-9]", "") : null);

        // ---------- Subject ----------
        v.put("subject", paid
                ? "Invoice " + invoiceNo + " from " + orgName + " – Paid"
                : "Invoice " + invoiceNo + " from " + orgName + " – " + amountDue + " due by " + dueDate);

        // ---------- Invoice ----------
        v.put("paid", paid);
        v.put("hasAttachment", hasPdfAttachment);
        v.put("invoiceNumber", invoiceNo);
        v.put("issueDate", formatDate(invoice.getInvoiceDate()));
        v.put("serviceName", serviceName);
        v.put("amountDue", amountDue);
        v.put("amountPaid", amountTotal);
        v.put("amountNote", amountNote(invoice));
        v.put("dueDate", dueDate);

        // ---------- Client ----------
        Contact contact = contact(invoice);
        Company company = company(invoice);
        v.put("clientName", firstNonBlank(contact != null ? contact.getName() : null, "Sir/Madam"));
        v.put("companyName", company != null ? firstNonBlank(company.getName(), "your company") : "your company");

        // ---------- Links ----------
        v.put("documentLink", buildDocumentLink(invoice.getPublicUuid()));
        v.put("paymentLink", paid ? null : buildPaymentLink(invoice, org, due));

        // ---------- Bank details (central Organization record only) ----------
        boolean bankPresent = !paid && org != null && org.isBankAccountPresent()
                && trimToNull(org.getAccountNo()) != null;
        v.put("bankPresent", bankPresent);
        if (bankPresent) {
            v.put("bankAccountName", trimToNull(org.getAccountHolderName()));
            v.put("bankAccountNo", trimToNull(org.getAccountNo()));
            v.put("bankNameBranch", joinNonBlank(", ", org.getBankName(), org.getBranch()));
            v.put("bankIfsc", trimToNull(org.getIfscCode()));
            v.put("bankSwift", gstType(invoice) == GstRegistrationType.INTERNATIONAL
                    ? trimToNull(org.getSwiftCode()) : null);
            v.put("upiId", trimToNull(org.getUpiId()));
        }

        // ---------- Account manager ----------
        User manager = accountManager(invoice);
        v.put("accountManagerName", manager != null ? trimToNull(manager.getFullName()) : null);
        v.put("accountManagerPhone", orgPhone);   // swap for the user's own mobile if User has one
        v.put("accountManagerPhoneHref", v.get("orgPhoneHref"));

        return v;
    }

    private String buildPaymentLink(Invoice invoice, Organization org, BigDecimal due) {
        String template = trimToNull(paymentLinkTemplate);
        if (template == null && org != null) {
            template = trimToNull(org.getPaymentPageLink());
        }
        if (template == null) {
            return null;
        }
        return template
                .replace("{invoiceNo}", urlEncode(invoice.getInvoiceNumber()))
                .replace("{uuid}", urlEncode(invoice.getPublicUuid()))
                .replace("{amount}", due.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private static String urlEncode(String s) {
        return s == null ? "" : URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // =====================================================
    // PLAIN-TEXT ALTERNATIVE (same wording as the spec)
    // =====================================================

    private String buildPlainText(Map<String, Object> v) {
        boolean paid = Boolean.TRUE.equals(v.get("paid"));
        StringBuilder sb = new StringBuilder();

        sb.append("Dear ").append(v.get("clientName")).append(",\n\n")
                .append("Thank you for your business. Please find ")
                .append(Boolean.TRUE.equals(v.get("hasAttachment")) ? "attached" : "below")
                .append(" the invoice for ").append(v.get("serviceName"))
                .append(" for ").append(v.get("companyName")).append(".");
        if (paid) {
            sb.append(" We have received your payment for this invoice. Thank you!");
        }
        sb.append("\n\n")
                .append("Invoice summary\n")
                .append("Invoice No.:    ").append(v.get("invoiceNumber")).append('\n')
                .append("Invoice date:   ").append(v.get("issueDate")).append('\n')
                .append("Service:        ").append(v.get("serviceName")).append('\n');
        if (paid) {
            sb.append("Amount paid:    ").append(v.get("amountPaid")).append(' ').append(v.get("amountNote")).append('\n')
                    .append("Status:         Paid\n\n");
        } else {
            sb.append("Amount due:     ").append(v.get("amountDue")).append(' ').append(v.get("amountNote")).append('\n')
                    .append("Due date:       ").append(v.get("dueDate")).append("\n\n");
        }

        if (v.get("paymentLink") != null) {
            sb.append("Pay online: ").append(v.get("paymentLink")).append('\n');
        }
        if (v.get("documentLink") != null) {
            sb.append("View invoice: ").append(v.get("documentLink")).append('\n');
        }
        if (v.get("paymentLink") != null || v.get("documentLink") != null) {
            sb.append('\n');
        }

        if (Boolean.TRUE.equals(v.get("bankPresent"))) {
            sb.append("Bank transfer details\n");
            appendRow(sb, "Account name:   ", v.get("bankAccountName"));
            appendRow(sb, "Account no.:    ", v.get("bankAccountNo"));
            appendRow(sb, "Bank & branch:  ", v.get("bankNameBranch"));
            appendRow(sb, "IFSC:           ", v.get("bankIfsc"));
            appendRow(sb, "SWIFT:          ", v.get("bankSwift"));
            appendRow(sb, "UPI ID:         ", v.get("upiId"));
            sb.append('\n');
        }

        if (!paid) {
            sb.append("Please mention the invoice number ").append(v.get("invoiceNumber"))
                    .append(" in your payment reference. If you have already paid, kindly share the ")
                    .append("payment details so we can update our records.\n\n");
        }

        sb.append("For any billing questions, reply to this email");
        if (v.get("accountManagerName") != null) {
            sb.append(" or contact ").append(v.get("accountManagerName"));
            if (v.get("accountManagerPhone") != null) {
                sb.append(" at ").append(v.get("accountManagerPhone"));
            }
        }
        sb.append(".\n\n")
                .append("Warm regards,\nAccounts Team, ").append(v.get("orgName")).append('\n')
                .append(v.get("orgWebsite")).append("\n\n")
                .append("Note: ").append(v.get("orgName"))
                .append(" will never ask you to pay into a different bank account by email or WhatsApp. ")
                .append("If you receive such a request, please call us");
        if (v.get("orgPhone") != null) {
            sb.append(" on ").append(v.get("orgPhone"));
        }
        sb.append(" to verify before making any payment.\n");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, String label, Object value) {
        if (value != null) {
            sb.append(label).append(value).append('\n');
        }
    }

    // =====================================================
    // RESULT
    // =====================================================

    public record InvoiceEmail(String subject, String htmlBody, String plainTextBody) {
    }
}
