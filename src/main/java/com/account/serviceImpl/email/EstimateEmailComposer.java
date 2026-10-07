package com.account.serviceImpl.email;

import com.account.domain.Contact;
import com.account.domain.Organization;
import com.account.domain.User;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.repository.OrganizationRepository;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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
 * and reply-to) following the "Corpseed Estimate & Invoice Notification
 * Templates" spec. Recipient selection stays in EmailServiceImpl.
 *
 * IMPORTANT: call this inside an open transaction. It reads lazy associations
 * (company, unit, contacts, line items, createdBy). sendEstimateToClient(...)
 * is already @Transactional, so calling it from there is safe. If you ever move
 * sending to @Async, compose the email before handing off.
 */
@Component
@RequiredArgsConstructor
public class EstimateEmailComposer {

    public static final String TEMPLATE_NAME = "estimate-email-template";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final String GOVT_FEE_NOTE =
            "Please note that government fees, timelines and approvals are set by "
                    + "the respective authority and may change.";

    private final ITemplateEngine templateEngine;
    private final OrganizationRepository organizationRepository;

    /** Base URL of the public estimate page, e.g. https://app.corpseed.com/estimate/view */
    @Value("${app.estimate.public-view-url:}")
    private String publicViewBaseUrl;

    /** Set true only once the public estimate page has an "Accept" button. */
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

    // =====================================================
    // TEMPLATE VARIABLES
    // =====================================================

    private Map<String, Object> buildVariables(Estimate estimate, Organization org, boolean hasPdfAttachment) {
        Map<String, Object> v = new LinkedHashMap<>();

        String currency = estimate.getCurrency();
        boolean revised = estimate.getVersion() != null && estimate.getVersion() > 1;
        boolean zeroRated = estimate.isZeroRatedSupply();

        // ---------- Organization ----------
        String orgName = firstNonBlank(org != null ? org.getName() : null, "Corpseed");
        String orgWebsite = org != null ? trimToNull(org.getWebsite()) : null;

        v.put("orgName", orgName);
        v.put("orgLogoUrl", org != null ? trimToNull(org.getLogoUrl()) : null);
        v.put("orgWebsite", orgWebsite);
        v.put("orgWebsiteHref", toHref(orgWebsite));
        v.put("orgGstNo", org != null ? trimToNull(org.getGstNo()) : null);
        v.put("orgAddress", org != null ? joinNonBlank(", ",
                org.getAddressLine1(), org.getAddressLine2(), org.getCity(),
                org.getState(), org.getPinCode()) : null);

        // ---------- Estimate ----------
        String estimateNo = estimate.getEstimateNumber();
        String serviceName = firstNonBlank(estimate.getSolutionName(), "our services");

        v.put("subject", (revised ? "Revised " : "") + "Estimate " + estimateNo
                + " for " + serviceName + " – " + orgName);
        v.put("revised", revised);
        v.put("hasAttachment", hasPdfAttachment);
        v.put("estimateNumber", estimateNo);
        v.put("serviceName", serviceName);
        v.put("issueDate", formatDate(estimate.getEstimateDate()));
        v.put("validUntil", formatDate(estimate.getValidUntil()));
        v.put("paymentTerm", trimToNull(estimate.getPaymentTerm()));
        v.put("customerNotes", trimToNull(estimate.getCustomerNotes()));
        v.put("govtFeeNote", GOVT_FEE_NOTE);

        // ---------- Client ----------
        Contact contact = resolveContact(estimate);
        v.put("clientName", firstNonBlank(contact != null ? contact.getName() : null, "Sir/Madam"));
        v.put("companyName", estimate.getCompany() != null
                ? firstNonBlank(estimate.getCompany().getName(), "your company")
                : "your company");

        // ---------- Amounts ----------
        v.put("amountTotal", money(estimate.getGrandTotal(), currency, false));
        v.put("amountTotalNote", zeroRated ? "(zero-rated, no GST)" : "(inclusive of GST)");
        v.put("subTotal", money(estimate.getSubTotalExGst(), currency, true));
        v.put("lineItems", buildLineRows(estimate, currency));
        v.put("taxRows", buildTaxRows(estimate, currency, zeroRated));

        // ---------- Online link ----------
        String documentLink = buildDocumentLink(estimate.getPublicUuid());
        v.put("documentLink", documentLink);
        v.put("acceptOnline", documentLink != null && onlineAcceptEnabled);

        // ---------- Account manager ----------
        User manager = estimate.getCreatedBy();
        String managerName = manager != null ? trimToNull(manager.getFullName()) : null;
        // ASSUMPTION: User has getEmail(). Rename if your field differs.
        String managerEmail = manager != null ? validEmailOrNull(manager.getEmail()) : null;
        // TODO: replace with the manager's own mobile if User has one (e.g. manager.getContactNo()).
        String managerPhone = org != null ? trimToNull(org.getPhone()) : null;

        v.put("accountManagerName", managerName);
        v.put("accountManagerEmail", managerEmail);
        v.put("accountManagerPhone", managerPhone);
        v.put("accountManagerPhoneHref", managerPhone != null ? managerPhone.replaceAll("[^+0-9]", "") : null);

        return v;
    }

    private List<LineRow> buildLineRows(Estimate estimate, String currency) {
        if (estimate.getLineItems() == null) {
            return List.of();
        }
        return estimate.getLineItems().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(EstimateLineItem::getDisplayOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(item -> new LineRow(
                        firstNonBlank(item.getItemName(), "Item"),
                        trimToNull(item.getDescription()),
                        item.getQuantity() != null ? item.getQuantity() : 1,
                        percent(item.getGstRate()),
                        money(item.getLineTotalExGst(), currency, true)
                ))
                .toList();
    }

    private List<AmountRow> buildTaxRows(Estimate estimate, String currency, boolean zeroRated) {
        List<AmountRow> rows = new ArrayList<>();

        if (zeroRated) {
            rows.add(new AmountRow(
                    estimate.isSezSupply()
                            ? "GST (zero-rated SEZ supply)"
                            : "GST (zero-rated export of services)",
                    money(BigDecimal.ZERO, currency, true)));
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
    // PLAIN-TEXT ALTERNATIVE (mirrors the spec's text layout;
    // multipart text+HTML improves deliverability)
    // =====================================================

    private String buildPlainText(Map<String, Object> v) {
        StringBuilder sb = new StringBuilder();

        if (Boolean.TRUE.equals(v.get("revised"))) {
            sb.append("REVISED ESTIMATE: this version replaces the estimate we shared earlier.\n\n");
        }

        sb.append("Dear ").append(v.get("clientName")).append(",\n\n")
                .append("Thank you for choosing ").append(v.get("orgName")).append(". Please find ")
                .append(Boolean.TRUE.equals(v.get("hasAttachment")) ? "attached" : "below")
                .append(" our estimate for ").append(v.get("serviceName"))
                .append(" for ").append(v.get("companyName")).append(".\n\n")

                .append("Estimate summary\n")
                .append("Estimate No.:   ").append(v.get("estimateNumber")).append('\n')
                .append("Date:           ").append(v.get("issueDate")).append('\n')
                .append("Service:        ").append(v.get("serviceName")).append('\n');
        if (v.get("paymentTerm") != null) {
            sb.append("Payment terms:  ").append(v.get("paymentTerm")).append('\n');
        }
        sb.append("Total amount:   ").append(v.get("amountTotal")).append(' ')
                .append(v.get("amountTotalNote")).append('\n')
                .append("Valid until:    ").append(v.get("validUntil")).append("\n\n");

        if (v.get("documentLink") != null) {
            sb.append("View estimate online: ").append(v.get("documentLink")).append("\n\n");
        }

        sb.append("What happens next\n")
                .append("1. Review the estimate and reply to this email")
                .append(Boolean.TRUE.equals(v.get("acceptOnline")) ? ", or click \"Accept\" on the online estimate," : "")
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

        sb.append("Warm regards,\nTeam ").append(v.get("orgName")).append('\n');
        if (v.get("orgWebsite") != null) {
            sb.append(v.get("orgWebsite")).append('\n');
        }
        return sb.toString();
    }

    // =====================================================
    // HELPERS
    // =====================================================

    private static Contact resolveContact(Estimate estimate) {
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

    private String buildDocumentLink(String publicUuid) {
        String base = trimToNull(publicViewBaseUrl);
        if (base == null || trimToNull(publicUuid) == null) {
            return null;
        }
        return (base.endsWith("/") ? base : base + "/") + publicUuid;
    }

    private static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "-";
    }

    private static String percent(BigDecimal rate) {
        if (rate == null) {
            return "0%";
        }
        return rate.stripTrailingZeros().toPlainString() + "%";
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /**
     * Indian digit grouping: 35400 -> ₹35,400 ; 1234567.5 -> ₹12,34,567.50
     * (java.text.DecimalFormat cannot do lakh/crore grouping on its own.)
     */
    static String money(BigDecimal amount, String currency, boolean withPaise) {
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonBlank(String value, String fallback) {
        String t = trimToNull(value);
        return t != null ? t : fallback;
    }

    private static String joinNonBlank(String separator, String... parts) {
        String joined = Stream.of(parts)
                .map(EstimateEmailComposer::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    // =====================================================
    // VIEW MODELS
    // (plain getters, not records, so Thymeleaf/SpEL resolves item.name etc.)
    // =====================================================

    @Getter
    @AllArgsConstructor
    public static class LineRow {
        private final String name;
        private final String description;
        private final int quantity;
        private final String gstRate;
        private final String amount;
    }

    @Getter
    @AllArgsConstructor
    public static class AmountRow {
        private final String label;
        private final String amount;
    }

    public record EstimateEmail(
            String subject,
            String htmlBody,
            String plainTextBody,
            String replyTo
    ) {
    }
}
