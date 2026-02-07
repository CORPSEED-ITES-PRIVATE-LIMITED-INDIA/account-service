package com.account.controller.unbilled;

import com.account.domain.UnbilledStatus;
import com.account.dto.unbilled.*;
import com.account.service.PaymentService;
import com.account.service.UnbilledService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Unbilled Invoices", description = "Operations related to unbilled / proforma / advance invoices (approval flow for accounts team)")
@RestController
@RequestMapping("/accountService/api/v1/unbilled-invoices")
@RequiredArgsConstructor
@Validated
public class UnbilledInvoiceController {

    private final PaymentService paymentService;
    private final UnbilledService unbilledService;


    // ────────────────────────────────────────────────
    //  Approve unbilled invoice (usually done by Accounts)
    // ────────────────────────────────────────────────
    @Operation(
            summary = "Approve unbilled invoice",
            description = "Approves the unbilled invoice, changes status to APPROVED, " +
                    "and triggers generation of the final tax invoice (GST invoice)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully approved and invoice generated"),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation error", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unbilled invoice not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Invoice already approved / wrong status", content = @Content)
    })
    @PostMapping("/{unbilledId}/approve")
    public ResponseEntity<UnbilledInvoiceApprovalResponseDto> approveUnbilledInvoice(
            @PathVariable @Parameter(description = "ID of the unbilled invoice") Long unbilledId,
            @Valid @RequestBody UnbilledInvoiceApprovalRequestDto request) {

        UnbilledInvoiceApprovalResponseDto response =
                paymentService.approveUnbilledInvoice(unbilledId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get list of unbilled invoices (paginated)",
            description = "Returns a paginated list of unbilled invoices. Page numbering starts from 1. Default sorting: createdAt DESC."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or filter parameters", content = @Content)
    })
    @GetMapping("/list")
    public ResponseEntity<List<UnbilledInvoiceSummaryDto>> getUnbilledInvoicesList(
            @RequestParam(value = "status", required = false)
            @Parameter(description = "Filter by unbilled invoice status") UnbilledStatus status,

            @RequestParam(value = "userId", required = false)
            @Parameter(description = "Filter by user who created or approved the record") Long userId,

            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("Page and size must be greater than 0");
        }

        List<UnbilledInvoiceSummaryDto> list =
                paymentService.getUnbilledInvoicesList(userId, status, page - 1, size);

        return ResponseEntity.ok(list);
    }

    @Operation(
            summary = "Get count of unbilled invoices",
            description = "Returns only the total number of unbilled invoices matching the optional filters (status and/or userId)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
    })
    @GetMapping("/count")
    public ResponseEntity<Long> getUnbilledInvoicesCount(
            @RequestParam(value = "status", required = false)
            @Parameter(description = "Filter by unbilled invoice status") UnbilledStatus status,

            @RequestParam(value = "userId", required = false)
            @Parameter(description = "Filter by user who created or approved the record") Long userId
    ) {
        long count = paymentService.getUnbilledInvoicesCount(userId, status);
        return ResponseEntity.ok(count);
    }


    @Operation(
            summary = "Search unbilled invoices by unbilled number and/or company name (paginated)",
            description = "Returns a paginated list of unbilled invoices matching the search criteria (partial match, case-insensitive)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or search parameters", content = @Content)
    })
    @GetMapping("/search")
    public ResponseEntity<List<UnbilledInvoiceSummaryDto>> searchUnbilledInvoices(
            @RequestParam(value = "unbilledNumber", required = false)
            @Parameter(description = "Partial unbilled number to search for") String unbilledNumber,

            @RequestParam(value = "companyName", required = false)
            @Parameter(description = "Partial company name to search for") String companyName,

            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("Page and size must be greater than 0");
        }

        List<UnbilledInvoiceSummaryDto> list =
                paymentService.searchUnbilledInvoices(unbilledNumber, companyName, page - 1, size);

        return ResponseEntity.ok(list);
    }

    @Operation(
            summary = "Get count of unbilled invoices matching search criteria",
            description = "Returns the total number of unbilled invoices matching the optional search filters (unbilledNumber and/or companyName)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters", content = @Content)
    })
    @GetMapping("/search/count")
    public ResponseEntity<Long> countSearchUnbilledInvoices(
            @RequestParam(value = "unbilledNumber", required = false)
            @Parameter(description = "Partial unbilled number to search for") String unbilledNumber,

            @RequestParam(value = "companyName", required = false)
            @Parameter(description = "Partial company name to search for") String companyName
    ) {
        long count = paymentService.countSearchUnbilledInvoices(unbilledNumber, companyName);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full detailed invoice by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice details"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<UnbilledInvoiceDetailDto> getInvoiceDetail(
            @PathVariable Long id,
            @RequestParam Long userId
    ) {
        UnbilledInvoiceDetailDto unbilledInvoiceDetailDto = paymentService.getUnbilledInvoice(id, userId);
        return ResponseEntity.ok(unbilledInvoiceDetailDto);
    }



    @PostMapping("/getUnbiledInvoices")
    public ResponseEntity<List<UnbilledInvoiceSummaryDto>> searchUnbilledInvoices(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestBody(required = false) UnbilledInvoiceFilterRequest filter
    ) {
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 20;

        return ResponseEntity.ok(
                unbilledService.searchUnbilledInvoices(
                        userId,
                        filter,
                        page - 1,
                        size
                )
        );
    }
}