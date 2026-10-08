package com.account.serviceImpl.email;

import com.account.domain.Contact;
import com.account.domain.Organization;
import com.account.domain.User;
import com.account.domain.estimate.Estimate;
import com.account.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds the client-facing estimate email (subject, HTML body, plain-text body
 * and reply-to) exactly as per the "Estimate email" spec:
 *
 *   Subject: Estimate {{estimate_no}} for {{service_name}} – Corpseed
 *   Attachment: Estimate_{{estimate_no}}.pdf
 *
 * Recipient selection stays in EmailServiceImpl.
 *
 * IMPORTANT: call this inside an open transaction. It reads lazy associations
 * (company, unit, contacts, createdBy).
 */
@Component
@RequiredArgsConstructor
public class EstimateEmailComposer {

    private static final Logger log = LogManager.getLogger(EstimateEmailComposer.class);

    public static final String TEMPLATE_NAME = "estimate-email-template";

    public static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final String GOVT_FEE_NOTE =
            "Please note that government fees, timelines and approvals are set by "
                    + "the respective authority and may change.";

    private final ITemplateEngine templateEngine;
    private final OrganizationRepository organizationRepository;

    /** Brand used in subject, greeting and sign-off ("Team Corpseed"). */
    @Value("${app.mail.brand-name:Corpseed}")
    private String brandName;

    /** Website shown under the sign-off. */
    @Value("${app.mail.brand-website:www.corpseed.com}")
    private String brandWebsite;

    /**
     * Public page for the estimate. Either:
     *   https://app.corpseed.com/estimate/view            -> /{uuid} is appended
     *   https://app.corpseed.com/estimate/{uuid}/view     -> {uuid} is replaced
     * Leave blank to fall back to the backend PDF link below.
     */
    @Value("${app.estimate.public-view-url:}")
    private String publicViewUrl;

    /**
     * Public base URL of THIS backend, e.g. https://api.corpseed.com
     * Used when public-view-url is blank: link becomes
     * {base}/public/estimates/{uuid}/pdf (served by EstimatePublicController).
     */
    @Value("${app.public-base-url:}")
    private String publicBaseUrl;

    /** Set true only when the public estimate page has an "Accept" button. */
    @Value("${app.estimate.online-accept-enabled:false}")
    private boolean onlineAcceptEnabled;

    // =====================================================
    // PUBLIC API
    // =====================================================

    public EstimateEmail compose(Estimate estimate, boolean hasPdfAttachment) {
        Objects.requireNonNull(estimate, "estimate is required");

        Organization org = organizationRepository.findTopOrganization().orElse(null);
        Map<String, Object> vars = buildVariables(estimate, org, hasPdfAttachment);

        Context context = new Context(Locale.ENGLISH);
        context.setVariables(vars);
        String html = templateEngine.process(TEMPLATE_NAME, context);

        return new EstimateEmail(
                (String) vars.get("subject"),
                html,
                buildPlainText(vars),
                (String) vars.get("accountManagerEmail")   // replies go to the account manager
        );
    }

    /** Public link to the estimate, or null if no URL is configured. */
    public String buildDocumentLink(String publicUuid) {
        String uuid = trimToNull(publicUuid);
        if (uuid == null) {
            return null;
        }

        String viewUrl = trimToNull(publicViewUrl);
        if (viewUrl != null) {
            if (viewUrl.contains("{uuid}")) {
                return viewUrl.replace("{uuid}", uuid);
            }
            return (viewUrl.endsWith("/") ? viewUrl : viewUrl + "/") + uuid;
        }

        String base = trimToNull(publicBaseUrl);
        if (base != null) {
            return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                    + "/public/estimates/" + uuid + "/pdf";
        }

        log.warn("Estimate link not added to email: set app.estimate.public-view-url "
                + "or app.public-base-url in application.properties");
        return null;
    }

    // =====================================================
    // TEMPLATE VARIABLES
    // =====================================================

    private Map<String, Object> buildVariables(Estimate estimate, Organization org, boolean hasPdfAttachment) {
        Map<String, Object> v = new LinkedHashMap<>();

        String currency = estimate.getCurrency();
        boolean zeroRated = estimate.isZeroRatedSupply();

        // ---------- Brand ----------
        String orgName = firstNonBlank(brandName, "Corpseed");
        String website = firstNonBlank(brandWebsite,
                org != null ? firstNonBlank(org.getWebsite(), "www.corpseed.com") : "www.corpseed.com");

        v.put("orgName", orgName);
        v.put("orgWebsite", stripScheme(website));
        v.put("orgWebsiteHref", toHref(website));

        // ---------- Estimate ----------
        String estimateNo = estimate.getEstimateNumber();
        String serviceName = firstNonBlank(estimate.getSolutionName(), "our services");

        v.put("subject", "Estimate " + estimateNo + " for " + serviceName + " – " + orgName);
        v.put("hasAttachment", hasPdfAttachment);
        v.put("estimateNumber", estimateNo);
        v.put("serviceName", serviceName);
        v.put("issueDate", formatDate(estimate.getEstimateDate()));
        v.put("validUntil", formatDate(estimate.getValidUntil()));
        v.put("govtFeeNote", GOVT_FEE_NOTE);

        // ---------- Client ----------
        Contact contact = resolveContact(estimate);
        v.put("clientName", firstNonBlank(contact != null ? contact.getName() : null, "Sir/Madam"));
        v.put("companyName", estimate.getCompany() != null
                ? firstNonBlank(estimate.getCompany().getName(), "your company")
                : "your company");

        // ---------- Amount ----------
        v.put("amountTotal", money(estimate.getGrandTotal(), currency, false));
        v.put("amountTotalNote", zeroRated ? "(zero-rated, no GST)" : "(inclusive of GST)");

        // ---------- Online link ----------
        String documentLink = buildDocumentLink(estimate.getPublicUuid());
        v.put("documentLink", documentLink);
        v.put("acceptOnline", documentLink != null && onlineAcceptEnabled);

        // ---------- Account manager ----------
        User manager = estimate.getCreatedBy();
        String managerName = manager != null ? trimToNull(manager.getFullName()) : null;
        String managerEmail = manager != null ? validEmailOrNull(manager.getEmail()) : null;
        // Uses the company phone. If User has its own mobile field, use that here instead.
        String managerPhone = org != null ? trimToNull(org.getPhone()) : null;

        v.put("accountManagerName", managerName);
        v.put("accountManagerEmail", managerEmail);
        v.put("accountManagerPhone", managerPhone);
        v.put("accountManagerPhoneHref", managerPhone != null ? managerPhone.replaceAll("[^+0-9]", "") : null);

        return v;
    }

    // =====================================================
    // PLAIN-TEXT ALTERNATIVE (same wording as the spec)
    // =====================================================

    private String buildPlainText(Map<String, Object> v) {
        StringBuilder sb = new StringBuilder();

        sb.append("Dear ").append(v.get("clientName")).append(",\n\n")
                .append("Thank you for choosing ").append(v.get("orgName")).append(". Please find ")
                .append(Boolean.TRUE.equals(v.get("hasAttachment")) ? "attached" : "below")
                .append(" our estimate for ").append(v.get("serviceName"))
                .append(" for ").append(v.get("companyName")).append(".\n\n")

                .append("Estimate summary\n")
                .append("Estimate No.:   ").append(v.get("estimateNumber")).append('\n')
                .append("Date:           ").append(v.get("issueDate")).append('\n')
                .append("Service:        ").append(v.get("serviceName")).append('\n')
                .append("Total amount:   ").append(v.get("amountTotal")).append(' ')
                .append(v.get("amountTotalNote")).append('\n')
                .append("Valid until:    ").append(v.get("validUntil")).append("\n\n");

        if (v.get("documentLink") != null) {
            sb.append("View estimate online: ").append(v.get("documentLink")).append("\n\n");
        }

        sb.append("What happens next\n")
                .append("1. Review the estimate and reply to this email")
                .append(Boolean.TRUE.equals(v.get("acceptOnline")) ? " or click \"Accept\" on the online estimate" : "")
                .append(" to confirm.\n")
                .append("2. We will share the document checklist and start work as soon as we receive ")
                .append("your confirmation and advance payment.\n")
                .append("3. Your account manager will keep you updated at every stage until the approval ")
                .append("or certificate is issued.\n\n")
                .append(GOVT_FEE_NOTE).append("\n\n");

        if (v.get("accountManagerName") != null) {
            sb.append("If you have any questions or would like to discuss the scope, please contact your account manager:\n")
                    .append(joinNonBlank(" | ",
                            (String) v.get("accountManagerName"),
                            (String) v.get("accountManagerPhone"),
                            (String) v.get("accountManagerEmail")))
                    .append("\n\n");
        }

        sb.append("Warm regards,\nTeam ").append(v.get("orgName")).append('\n')
                .append(v.get("orgWebsite")).append('\n');
        return sb.toString();
    }

    // =====================================================
    // HELPERS (public static ones are reused by EstimatePdfService)
    // =====================================================

    public static Contact resolveContact(Estimate estimate) {
        if (estimate.getContact() != null) {
            return estimate.getContact();
        }
        return estimate.getUnit() != null ? estimate.getUnit().getPrimaryContact() : null;
    }

    private static String validEmailOrNull(String raw) {
        String e = trimToNull(raw);
        if (e == null) {
            return null;
        }
        e = e.toLowerCase(Locale.ROOT);
        return EMAIL_PATTERN.matcher(e).matches() ? e : null;
    }

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "-";
    }

    /**
     * Indian digit grouping: 35400 -> ₹35,400 ; 1234567.5 -> ₹12,34,567.50
     */
    public static String money(BigDecimal amount, String currency, boolean withPaise) {
        BigDecimal v = (amount == null ? BigDecimal.ZERO : amount)
                .setScale(withPaise ? 2 : 0, RoundingMode.HALF_UP);

        boolean negative = v.signum() < 0;
        String plain = v.abs().toPlainString();

        int dot = plain.indexOf('.');
        String intPart = dot >= 0 ? plain.substring(0, dot) : plain;
        String fraction = dot >= 0 ? plain.substring(dot) : "";

        String grouped;
        if (intPart.length() <= 3) {
            grouped = intPart;
        } else {
            String last3 = intPart.substring(intPart.length() - 3);
            String rest = intPart.substring(0, intPart.length() - 3);
            StringBuilder sb = new StringBuilder();
            int i = rest.length();
            while (i > 2) {
                sb.insert(0, "," + rest.substring(i - 2, i));
                i -= 2;
            }
            sb.insert(0, rest.substring(0, i));
            grouped = sb + "," + last3;
        }

        String symbol = (currency == null || "INR".equalsIgnoreCase(currency))
                ? "₹"
                : currency.toUpperCase(Locale.ROOT) + " ";

        return (negative ? "-" : "") + symbol + grouped + fraction;
    }

    private static String toHref(String website) {
        if (website == null) {
            return null;
        }
        return website.matches("(?i)^https?://.*") ? website : "https://" + website;
    }

    private static String stripScheme(String website) {
        return website == null ? null : website.replaceFirst("(?i)^https?://", "").replaceAll("/+$", "");
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static String firstNonBlank(String value, String fallback) {
        String t = trimToNull(value);
        return t != null ? t : fallback;
    }

    public static String joinNonBlank(String separator, String... parts) {
        String joined = Stream.of(parts)
                .map(EstimateEmailComposer::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    // =====================================================
    // RESULT
    // =====================================================

    public record EstimateEmail(
            String subject,
            String htmlBody,
            String plainTextBody,
            String replyTo
    ) {
    }
}
