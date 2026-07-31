package com.account.controller.invoice;

import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import com.account.dto.invoice.AdvanceTaxInvoiceApprovalRequestDto;
import com.account.dto.invoice.AdvanceTaxInvoiceCreateRequestDto;
import com.account.dto.invoice.AdvanceTaxInvoiceRejectionRequestDto;
import com.account.dto.invoice.AdvanceTaxInvoiceResponseDto;
import com.account.dto.invoice.ConfirmAdvanceInvoiceResponseDto;
import com.account.dto.invoice.ConfirmInvoiceEInvoiceRequestDto;
import com.account.service.AdvanceTaxInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/accountService/api/v1/advance-tax-invoice-requests"
)
@RequiredArgsConstructor
@Tag(
        name = "Advance Tax Invoice",
        description = "Advance Tax Invoice request and approval APIs"
)
public class AdvanceTaxInvoiceController {

    private final AdvanceTaxInvoiceService
            advanceTaxInvoiceService;

    // =====================================================
    // CREATE REQUEST
    // =====================================================

    @PostMapping
    @Operation(
            summary = "Create Advance Tax Invoice request"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Advance Tax Invoice request created"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Estimate or user not found"
            )
    })
    public ResponseEntity<AdvanceTaxInvoiceResponseDto>
    createRequest(
            @Valid
            @RequestBody
            AdvanceTaxInvoiceCreateRequestDto requestDto
    ) {

        AdvanceTaxInvoiceResponseDto response =
                advanceTaxInvoiceService.createRequest(
                        requestDto
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // =====================================================
    // APPROVE REQUEST
    // =====================================================

    @PutMapping("/{requestId}/approve")
    @Operation(
            summary = "Approve Advance Tax Invoice request",
            description = """
                    Approves a PENDING Advance Tax Invoice request
                    and generates an Invoice.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Request approved and Invoice generated"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User not authorized"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Advance Tax Invoice request not found"
            )
    })
    public ResponseEntity<AdvanceTaxInvoiceResponseDto>
    approveRequest(
            @PathVariable Long requestId,

            @Valid
            @RequestBody
            AdvanceTaxInvoiceApprovalRequestDto requestDto
    ) {

        AdvanceTaxInvoiceResponseDto response =
                advanceTaxInvoiceService.approveRequest(
                        requestId,
                        requestDto
                );

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // REJECT REQUEST
    // =====================================================

    @PutMapping("/{requestId}/reject")
    @Operation(
            summary = "Reject Advance Tax Invoice request",
            description = """
                    Rejects a PENDING Advance Tax Invoice request.
                    No Invoice is generated.
                    Only Accounts or Admin users can reject.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Advance Tax Invoice request rejected"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure or request is not PENDING"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User not authorized"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Advance Tax Invoice request not found"
            )
    })
    public ResponseEntity<AdvanceTaxInvoiceResponseDto>
    rejectRequest(
            @PathVariable Long requestId,

            @Valid
            @RequestBody
            AdvanceTaxInvoiceRejectionRequestDto requestDto
    ) {

        AdvanceTaxInvoiceResponseDto response =
                advanceTaxInvoiceService.rejectRequest(
                        requestId,
                        requestDto
                );

        return ResponseEntity.ok(response);
    }

    // =====================================================
    // GET REQUEST BY ID
    // =====================================================

    @GetMapping("/{requestId}")
    @Operation(
            summary = "Get Advance Tax Invoice request by ID"
    )
    public ResponseEntity<AdvanceTaxInvoiceResponseDto>
    getRequestById(
            @PathVariable Long requestId
    ) {

        return ResponseEntity.ok(
                advanceTaxInvoiceService.getRequestById(
                        requestId
                )
        );
    }

    // =====================================================
    // LIST REQUESTS
    // =====================================================

    @GetMapping
    @Operation(
            summary = "List Advance Tax Invoice requests",
            description = """
                    Accounts/Admin users can view all requests.
                    Sales users can view only requests raised by themselves.
                    """
    )
    public ResponseEntity<Page<AdvanceTaxInvoiceResponseDto>>
    getRequests(

            @RequestParam("userId")
            Long requestingUserId,

            @RequestParam(required = false)
            AdvanceTaxInvoiceRequestStatus status,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        return ResponseEntity.ok(
                advanceTaxInvoiceService.getRequests(
                        requestingUserId,
                        status,
                        page,
                        size
                )
        );
    }

    // =====================================================
    // CONFIRM E-INVOICE
    // =====================================================

    @PutMapping("/{invoiceId}/confirm-e-invoice")
    @Operation(
            summary = "Confirm/finalize Advance Tax Invoice and post Sales Voucher"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Advance Tax Invoice processed successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failure"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "User not authorized"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Advance Tax Invoice not found"
            )
    })
    public ResponseEntity<ConfirmAdvanceInvoiceResponseDto>
    confirmEInvoice(
            @PathVariable Long invoiceId,

            @Valid
            @RequestBody
            ConfirmInvoiceEInvoiceRequestDto request
    ) {

        return ResponseEntity.ok(
                advanceTaxInvoiceService
                        .confirmEInvoiceAndCreateProject(
                                invoiceId,
                                request
                        )
        );
    }
}