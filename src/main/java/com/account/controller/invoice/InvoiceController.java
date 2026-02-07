package com.account.controller.invoice;

import com.account.domain.InvoiceStatus;
import com.account.dto.invoice.InvoiceDetailDto;
import com.account.dto.invoice.InvoiceFilterRequest;
import com.account.dto.invoice.InvoiceSummaryDto;
import com.account.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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


    @PostMapping("/searchWithFilter")
    public ResponseEntity<List<InvoiceSummaryDto>> searchInvoices(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestBody(required = false) InvoiceFilterRequest filter
    ) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;

        return ResponseEntity.ok(
                invoiceService.searchInvoicesWithFilter(
                        userId,
                        filter,
                        page - 1,
                        size
                )
        );
    }



    @Operation(summary = "Search invoices by invoice number and/or company name (partial match)")
    @GetMapping("/search")
    public ResponseEntity<List<InvoiceSummaryDto>> searchInvoices(
            @RequestParam(value = "invoiceNumber", required = false) String invoiceNumber,
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("page and size must be positive");
        }

        List<InvoiceSummaryDto> result = invoiceService.searchInvoices(
                invoiceNumber,
                companyName,
                page - 1,
                size
        );

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Get count of invoices matching search criteria")
    @GetMapping("/search/count")
    public ResponseEntity<Long> countSearchInvoices(
            @RequestParam(value = "invoiceNumber", required = false) String invoiceNumber,
            @RequestParam(value = "companyName", required = false) String companyName
    ) {
        return ResponseEntity.ok(invoiceService.countSearchInvoices(invoiceNumber, companyName));
    }


    @GetMapping("/{id}")
    @Operation(summary = "Get full detailed invoice by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice details"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<InvoiceDetailDto> getInvoiceDetail(
            @PathVariable Long id,
            @RequestParam Long userId   // ← in production → replace with @AuthenticationPrincipal
    ) {
        InvoiceDetailDto detail = invoiceService.getInvoiceById(id, userId);
        return ResponseEntity.ok(detail);
    }




}