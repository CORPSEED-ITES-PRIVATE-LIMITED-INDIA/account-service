package com.account.config;

import com.account.domain.company.CompanyUnit;
import com.account.domain.Contact;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateLineItem;
import com.account.repository.ContactRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl {

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    private final ContactRepository contactRepository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );


    public void sendEmail(String[] emailTo, String[] ccPersons, String[] bccPersons) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(fromEmail);
            helper.setTo(emailTo);

            if (ccPersons != null && ccPersons.length > 0) {
                helper.setCc(ccPersons);
            }

            if (bccPersons != null && bccPersons.length > 0) {
                helper.setBcc(bccPersons);
            }

            helper.setSubject("Kaushal Singh wants you to join Corpeed ERP");
            helper.setText("Kaushal Here");

            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendEmail(String[] emailTo,
                          String[] ccPersons,
                          String[] bccPersons,
                          String subject,
                          String text,
                          Context context,
                          String templateName) {
        try {
            String html = templateEngine.process(templateName, context);

            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            helper.setFrom(fromEmail);
            helper.setTo(emailTo);

            if (ccPersons != null && ccPersons.length > 0) {
                helper.setCc(ccPersons);
            }

            if (bccPersons != null && bccPersons.length > 0) {
                helper.setBcc(bccPersons);
            }

            helper.setSubject(subject);
            helper.setText(html, true);

            javaMailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email using template", e);
        }
    }

    /**
     * Main method for estimate sending.
     * Fetches all contacts by estimate.unit.id, extracts all emails, and sends dynamic HTML email.
     *
     * @param estimate estimate entity
     * @return all valid unique recipient emails used for sending
     */
    public List<String> sendEstimateEmailToUnitContacts(Estimate estimate) {
        if (estimate == null) {
            throw new IllegalArgumentException("Estimate is required");
        }

        CompanyUnit unit = estimate.getUnit();
        if (unit == null || unit.getId() == null) {
            throw new IllegalArgumentException("Estimate has no company unit linked");
        }

        List<Contact> contacts = contactRepository.findByCompanyUnitIdAndDeleteStatusFalse(unit.getId());

        if (contacts == null || contacts.isEmpty()) {
            throw new IllegalArgumentException("No contacts found for company unit id: " + unit.getId());
        }

        List<String> recipientEmails = extractValidEmailsFromContacts(contacts);

        if (recipientEmails.isEmpty()) {
            throw new IllegalArgumentException("No valid email addresses found for contacts of company unit id: " + unit.getId());
        }

        Context context = buildEstimateEmailContext(estimate, unit, contacts);

        String subject = buildEstimateSubject(estimate);
        String html = templateEngine.process("estimate-email-template", context);

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(recipientEmails.toArray(new String[0]));
            helper.setSubject(subject);
            helper.setText(html, true);

            javaMailSender.send(mimeMessage);
            return recipientEmails;

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send estimate email", e);
        }
    }

    private String buildEstimateSubject(Estimate estimate) {
        String estimateNumber = estimate.getEstimateNumber() != null ? estimate.getEstimateNumber() : "N/A";
        String solutionName = estimate.getSolutionName() != null ? estimate.getSolutionName() : "Estimate";
        return "Estimate " + estimateNumber + " - " + solutionName;
    }

    private Context buildEstimateEmailContext(Estimate estimate,
                                              CompanyUnit unit,
                                              List<Contact> contacts) {
        Context context = new Context();

        String companyName = estimate.getCompany() != null ? safe(estimate.getCompany().getName()) : "Valued Client";
        String unitName = safe(unit.getUnitName());
        String unitAddress = buildUnitAddress(unit);
        String recipientNames = buildRecipientNames(contacts);

        String estimateDate = estimate.getEstimateDate() != null
                ? estimate.getEstimateDate().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                : "N/A";

        String validUntil = estimate.getValidUntil() != null
                ? estimate.getValidUntil().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                : "N/A";

        BigDecimal subtotal = nvl(estimate.getSubTotalExGst());
        BigDecimal totalGst = nvl(estimate.getTotalGstAmount());
        BigDecimal cgst = nvl(estimate.getCgstAmount());
        BigDecimal sgst = nvl(estimate.getSgstAmount());
        BigDecimal igst = nvl(estimate.getIgstAmount());
        BigDecimal grandTotal = nvl(estimate.getGrandTotal());

        List<Map<String, Object>> lineItemRows = new ArrayList<>();
        if (estimate.getLineItems() != null) {
            int srNo = 1;
            for (EstimateLineItem item : estimate.getLineItems()) {
                if (item == null) {
                    continue;
                }

                Map<String, Object> row = new HashMap<>();
                row.put("srNo", srNo++);
                row.put("itemName", safe(item.getItemName()));
                row.put("description", safe(item.getDescription()));
                row.put("quantity", item.getQuantity() != null ? item.getQuantity() : 0);
                row.put("unit", safe(item.getUnit()));
                row.put("unitPriceExGst", formatMoney(item.getUnitPriceExGst()));
                row.put("gstRate", item.getGstRate() != null ? item.getGstRate() : BigDecimal.ZERO);
                row.put("lineTotalExGst", formatMoney(item.getLineTotalExGst()));
                row.put("gstAmount", formatMoney(item.getGstAmount()));
                lineItemRows.add(row);
            }
        }

        context.setVariable("recipientNames", recipientNames);
        context.setVariable("companyName", companyName);
        context.setVariable("unitName", unitName);
        context.setVariable("unitAddress", unitAddress);

        context.setVariable("estimateNumber", safe(estimate.getEstimateNumber()));
        context.setVariable("estimateDate", estimateDate);
        context.setVariable("validUntil", validUntil);
        context.setVariable("solutionName", safe(estimate.getSolutionName()));
        context.setVariable("solutionType", safe(estimate.getSolutionType()));
        context.setVariable("customerNotes", safe(estimate.getCustomerNotes()));

        context.setVariable("lineItems", lineItemRows);

        context.setVariable("subTotalExGst", formatMoney(subtotal));
        context.setVariable("totalGstAmount", formatMoney(totalGst));
        context.setVariable("cgstAmount", formatMoney(cgst));
        context.setVariable("sgstAmount", formatMoney(sgst));
        context.setVariable("igstAmount", formatMoney(igst));
        context.setVariable("grandTotal", formatMoney(grandTotal));

        context.setVariable("currency", safe(estimate.getCurrency()));
        context.setVariable("createdByName",
                estimate.getCreatedBy() != null ? safe(estimate.getCreatedBy().getFullName()) : "Accounts Team");

        return context;
    }

    private List<String> extractValidEmailsFromContacts(List<Contact> contacts) {
        Set<String> uniqueEmails = new LinkedHashSet<>();

        for (Contact contact : contacts) {
            if (contact == null || contact.getEmails() == null || contact.getEmails().trim().isEmpty()) {
                continue;
            }

            String[] splitEmails = contact.getEmails().split(",");
            for (String email : splitEmails) {
                if (email == null) {
                    continue;
                }

                String cleaned = email.trim();
                if (!cleaned.isEmpty() && isValidEmail(cleaned)) {
                    uniqueEmails.add(cleaned);
                }
            }
        }

        return new ArrayList<>(uniqueEmails);
    }

    private String buildRecipientNames(List<Contact> contacts) {
        List<String> names = contacts.stream()
                .filter(Objects::nonNull)
                .map(Contact::getName)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (names.isEmpty()) {
            return "Sir/Madam";
        }

        return String.join(", ", names);
    }

    private String buildUnitAddress(CompanyUnit unit) {
        List<String> parts = new ArrayList<>();

        if (unit.getAddressLine1() != null && !unit.getAddressLine1().trim().isEmpty()) {
            parts.add(unit.getAddressLine1().trim());
        }
        if (unit.getAddressLine2() != null && !unit.getAddressLine2().trim().isEmpty()) {
            parts.add(unit.getAddressLine2().trim());
        }
        if (unit.getCity() != null && !unit.getCity().trim().isEmpty()) {
            parts.add(unit.getCity().trim());
        }
        if (unit.getState() != null && !unit.getState().trim().isEmpty()) {
            parts.add(unit.getState().trim());
        }
        if (unit.getCountry() != null && !unit.getCountry().trim().isEmpty()) {
            parts.add(unit.getCountry().trim());
        }
        if (unit.getPinCode() != null && !unit.getPinCode().trim().isEmpty()) {
            parts.add(unit.getPinCode().trim());
        }

        return String.join(", ", parts);
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        return safeValue.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}