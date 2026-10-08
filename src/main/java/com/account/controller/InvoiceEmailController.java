package com.account.controller;

import com.account.config.EmailServiceImpl;
import com.account.serviceImpl.email.InvoicePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 *   POST /api/invoices/{invoiceId}/send-email        send / resend to the client
 *   GET  /api/invoices/{invoiceId}/email-preview     email as the client sees it (not sent)
 *   GET  /api/invoices/{invoiceId}/pdf-preview       the attached PDF
 *   GET  /public/invoices/{publicUuid}/pdf           "View invoice" link in the email (no login)
 */
@RestController
@RequiredArgsConstructor
public class InvoiceEmailController {

    private final EmailServiceImpl emailService;
    private final InvoicePdfService invoicePdfService;

    @PostMapping("/api/invoices/{invoiceId}/send-email")
    public ResponseEntity<Map<String, Object>> sendInvoiceEmail(@PathVariable Long invoiceId) {
        List<String> sentTo = emailService.sendInvoiceToClient(invoiceId);
        return ResponseEntity.ok(Map.of(
                "invoiceId", invoiceId,
                "sentTo", sentTo,
                "message", "Invoice email sent successfully"
        ));
    }

    @GetMapping(value = "/api/invoices/{invoiceId}/email-preview", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> previewInvoiceEmail(@PathVariable Long invoiceId) {
        return ResponseEntity.ok(emailService.previewInvoiceEmail(invoiceId));
    }

    @GetMapping("/api/invoices/{invoiceId}/pdf-preview")
    public ResponseEntity<byte[]> previewInvoicePdf(@PathVariable Long invoiceId) {
        return pdf(invoicePdfService.generateById(invoiceId), false);
    }

    @GetMapping("/public/invoices/{publicUuid}/pdf")
    public ResponseEntity<byte[]> viewInvoicePdf(@PathVariable String publicUuid,
                                                 @RequestParam(defaultValue = "false") boolean download) {
        return pdf(invoicePdfService.generateByPublicUuid(publicUuid), download);
    }

    private ResponseEntity<byte[]> pdf(InvoicePdfService.PdfFile file, boolean download) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.builder(download ? "attachment" : "inline")
                                .filename(file.fileName())
                                .build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.content());
    }
}
