package com.account.controller.invoice;

import com.account.domain.InvoiceStatus;
import com.account.dto.invoice.InvoiceSummaryDto;
import com.account.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Invoices", description = "Operations for viewing generated tax invoices")
@RestController
@RequestMapping("/accountService/api/v1/invoices")
@RequiredArgsConstructor
@Validated
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "Get paginated list of tax invoices")
    @GetMapping("/list")
    public ResponseEntity<List<InvoiceSummaryDto>> getInvoicesList(
            @RequestParam(value = "status", required = false) InvoiceStatus status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be positive");
        }

        List<InvoiceSummaryDto> invoices =
                invoiceService.getInvoicesList(userId, status, page - 1, size);

        return ResponseEntity.ok(invoices);
    }

    @Operation(summary = "Get count of tax invoices")
    @GetMapping("/count")
    public ResponseEntity<Long> getInvoicesCount(
            @RequestParam(value = "status", required = false) InvoiceStatus status,
            @RequestParam(value = "createdById", required = false) Long createdById
    ) {
        return ResponseEntity.ok(invoiceService.getInvoicesCount(createdById, status));
    }
}