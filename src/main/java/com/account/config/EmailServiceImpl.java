package com.account.config;

import com.account.domain.Contact;
import com.account.domain.company.CompanyUnit;
import com.account.domain.estimate.Estimate;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.ContactRepository;
import com.account.repository.EstimateRepository;
import com.account.serviceImpl.email.EstimateEmailComposer;
import com.account.serviceImpl.pdf.EstimatePdfService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl {

    private static final Logger log = LogManager.getLogger(EmailServiceImpl.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+'-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Sender for estimate emails. Defaults to the SMTP login because Zoho
     * rejects a From address that isn't the mailbox itself or one of its aliases.
     */
    @Value("${app.mail.estimate-from:${spring.mail.username}}")
    private String estimateFromAddress;

    @Value("${app.mail.estimate-from-name:Corpseed}")
    private String estimateFromName;

    /**
     * true  -> if the PDF can't be generated, the email is NOT sent and the
     *          estimate stays DRAFT (recommended: the email promises an attachment).
     * false -> the email is sent without the PDF and says "Please find below".
     */
    @Value("${app.estimate.pdf.required:true}")
    private boolean pdfRequired;

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private TemplateEngine templateEngine;

    private final ContactRepository contactRepository;
    private final EstimateRepository estimateRepository;
    private final EstimateEmailComposer estimateEmailComposer;
    private final EstimatePdfService estimatePdfService;


    // =====================================================================
    // GENERIC SENDERS (unchanged)
    // =====================================================================

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


    // =====================================================================
    // ESTIMATE EMAIL
    // =====================================================================

    /**
     * Sends the estimate email (with Estimate_<no>.pdf attached) to the
     * estimate's own contact first, then every active contact of the unit.
     *
     * Must run inside the caller's transaction (sendEstimateToClient is
     * @Transactional) because the composer and PDF service read lazy associations.
     *
     * @return recipients actually used, or an empty list when no valid email
     *         exists (EstimateServiceImpl then raises ERR_NO_EMAIL as a 400).
     */
    public List<String> sendEstimateEmailToUnitContacts(Estimate estimate) {
        if (estimate == null) {
            throw new IllegalArgumentException("Estimate is required");
        }

        CompanyUnit unit = estimate.getUnit();
        if (unit == null || unit.getId() == null) {
            throw new IllegalArgumentException("Estimate has no company unit linked");
        }

        List<String> recipients = resolveEstimateRecipients(estimate, unit);
        if (recipients.isEmpty()) {
            log.warn("No valid recipient emails | estimate={} | unitId={}",
                    estimate.getEstimateNumber(), unit.getId());
            return List.of();
        }

        // ---------- PDF attachment ----------
        byte[] pdf = generatePdf(estimate);

        EstimateEmailComposer.EstimateEmail email =
                estimateEmailComposer.compose(estimate, pdf != null);

        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(estimateFromAddress, estimateFromName);
            helper.setTo(recipients.toArray(new String[0]));
            if (email.replyTo() != null) {
                helper.setReplyTo(email.replyTo());      // replies reach the account manager
            }
            helper.setSubject(email.subject());
            helper.setText(email.plainTextBody(), email.htmlBody());   // text + HTML alternative

            if (pdf != null) {
                helper.addAttachment(
                        EstimatePdfService.fileName(estimate),
                        new ByteArrayResource(pdf),
                        "application/pdf");
            }

            javaMailSender.send(mimeMessage);

            log.info("Estimate email sent | estimate={} | to={} | pdfAttached={} | subject={}",
                    estimate.getEstimateNumber(), recipients, pdf != null, email.subject());

            return recipients;

        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("Failed to send estimate email | estimate={} | error={}",
                    estimate.getEstimateNumber(), e.getMessage(), e);
            // Throwing rolls back sendEstimateToClient, so the estimate stays DRAFT.
            throw new RuntimeException("Failed to send estimate email", e);
        }
    }

    private byte[] generatePdf(Estimate estimate) {
        try {
            return estimatePdfService.generate(estimate);
        } catch (Exception e) {
            log.error("Estimate PDF generation failed | estimate={} | error={}",
                    estimate.getEstimateNumber(), e.getMessage(), e);
            if (pdfRequired) {
                // Rolls back sendEstimateToClient, so the estimate stays DRAFT.
                throw new RuntimeException("Could not generate the estimate PDF, email not sent", e);
            }
            return null;
        }
    }

    /**
     * Renders the estimate email exactly as the client would receive it,
     * using live data, without sending anything. A banner at the top shows
     * From / To / Reply-To / Subject / Attachment.
     */
    @Transactional(readOnly = true)
    public String previewEstimateEmail(Long estimateId) {
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Estimate not found with id: " + estimateId, "ESTIMATE_NOT_FOUND"));

        List<String> recipients = estimate.getUnit() != null
                ? resolveEstimateRecipients(estimate, estimate.getUnit())
                : List.of();

        EstimateEmailComposer.EstimateEmail email = estimateEmailComposer.compose(estimate, true);

        String banner = """
                <div style="font-family:Arial,sans-serif;font-size:13px;line-height:1.6;background:#fffbe6;\
                border-bottom:1px solid #e6d58a;padding:12px 16px;color:#333;">
                <strong>PREVIEW &ndash; not sent</strong><br>
                From: %s &lt;%s&gt;<br>
                To: %s<br>
                Reply-To: %s<br>
                Subject: %s<br>
                Attachment: %s
                </div>
                """.formatted(
                HtmlUtils.htmlEscape(estimateFromName),
                HtmlUtils.htmlEscape(estimateFromAddress),
                recipients.isEmpty()
                        ? "<span style='color:#b00020'>no valid email found on this unit's contacts</span>"
                        : HtmlUtils.htmlEscape(String.join(", ", recipients)),
                HtmlUtils.htmlEscape(email.replyTo() != null ? email.replyTo() : "-"),
                HtmlUtils.htmlEscape(email.subject()),
                HtmlUtils.htmlEscape(EstimatePdfService.fileName(estimate)));

        return email.htmlBody().replaceFirst("(?i)(<body[^>]*>)", "$1" + Matcher.quoteReplacement(banner));
    }

    /**
     * Recipient order (first one is saved as estimate.sentToEmail):
     *   1. the contact chosen on the estimate
     *   2. the unit's primary contact, then secondary contact
     *   3. every other active contact of the unit (primary/secondary flags first)
     * Contacts with deleteStatus = true or isDeleted = true are skipped.
     */
    private List<String> resolveEstimateRecipients(Estimate estimate, CompanyUnit unit) {
        List<Contact> ordered = new ArrayList<>();

        ordered.add(estimate.getContact());
        ordered.add(unit.getPrimaryContact());
        ordered.add(unit.getSecondaryContact());

        List<Contact> unitContacts =
                contactRepository.findByCompanyUnitIdAndDeleteStatusFalse(unit.getId());
        if (unitContacts != null) {
            unitContacts.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .comparing((Contact c) -> !c.isPrimaryForUnit())
                            .thenComparing(c -> !c.isSecondaryForUnit()))
                    .forEach(ordered::add);
        }

        List<Contact> active = ordered.stream()
                .filter(Objects::nonNull)
                .filter(c -> !c.isDeleted() && !c.isDeleteStatus())
                .toList();

        return extractValidEmailsFromContacts(active);
    }

    /**
     * Contact.emails may be comma/semicolon/space separated or a JSON array
     * string. Output is lower-cased, validated and deduplicated in order.
     */
    private List<String> extractValidEmailsFromContacts(List<Contact> contacts) {
        Set<String> uniqueEmails = new LinkedHashSet<>();

        for (Contact contact : contacts) {
            if (contact == null || contact.getEmails() == null || contact.getEmails().isBlank()) {
                continue;
            }

            String normalized = contact.getEmails().replaceAll("[\\[\\]\"]", " ");
            for (String raw : normalized.split("[,;\\s]+")) {
                String email = raw.trim().toLowerCase(Locale.ROOT);
                if (!email.isEmpty() && isValidEmail(email)) {
                    uniqueEmails.add(email);
                }
            }
        }

        return new ArrayList<>(uniqueEmails);
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }
}
