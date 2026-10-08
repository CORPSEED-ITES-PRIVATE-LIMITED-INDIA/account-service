package com.account.controller;

import com.account.serviceImpl.pdf.EstimatePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Target of the "View estimate online" link in the estimate email
 * (used when app.estimate.public-view-url is blank).
 *
 *   GET /public/estimates/{publicUuid}/pdf            -> opens in the browser
 *   GET /public/estimates/{publicUuid}/pdf?download=true -> downloads
 *   GET /public/estimates/preview/{estimateId}/pdf     -> internal preview (keep authenticated)
 *
 * The /public/estimates/** path must be permitted without login in your
 * Spring Security config (see notes). The UUID is random, so it isn't guessable.
 */
@RestController
@RequiredArgsConstructor
public class EstimatePublicController {

    private final EstimatePdfService estimatePdfService;

    @GetMapping("/public/estimates/{publicUuid}/pdf")
    public ResponseEntity<byte[]> viewEstimatePdf(@PathVariable String publicUuid,
                                                  @RequestParam(defaultValue = "false") boolean download) {
        return pdfResponse(estimatePdfService.generateByPublicUuid(publicUuid), download);
    }

    /** Internal: check the PDF before sending. Do NOT add this path to permitAll. */
    @GetMapping("/api/estimates/{estimateId}/pdf-preview")
    public ResponseEntity<byte[]> previewEstimatePdf(@PathVariable Long estimateId) {
        return pdfResponse(estimatePdfService.generateById(estimateId), false);
    }

    private ResponseEntity<byte[]> pdfResponse(EstimatePdfService.PdfFile file, boolean download) {
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
