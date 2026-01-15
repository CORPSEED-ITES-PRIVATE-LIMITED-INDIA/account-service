package com.account.controller.unbilled;

import com.account.domain.UnbilledStatus;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;
import com.account.service.PaymentService;
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



    // ────────────────────────────────────────────────
    //  Optional: Reject endpoint (you can implement later)
    // ────────────────────────────────────────────────
    /*
    @Operation(summary = "Reject unbilled invoice", description = "Rejects the unbilled invoice and sets rejection reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully rejected"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "404", description = "Not found")
    })
    @PostMapping("/{unbilledId}/reject")
    public ResponseEntity<UnbilledInvoiceApprovalResponseDto> rejectUnbilledInvoice(
            @PathVariable Long unbilledId,
            @Valid @RequestBody UnbilledInvoiceRejectionRequestDto request) {
        // TODO: implement rejection logic
        return ResponseEntity.ok(new UnbilledInvoiceApprovalResponseDto(...));
    }
    */

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


}