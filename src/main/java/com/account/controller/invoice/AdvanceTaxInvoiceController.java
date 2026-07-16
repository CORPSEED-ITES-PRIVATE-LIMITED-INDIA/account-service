package com.account.controller.invoice;

import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import com.account.dto.invoice.*;
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

    private final AdvanceTaxInvoiceService advanceTaxInvoiceService;

    @PostMapping
    @Operation(
            summary = "Create Advance Tax Invoice request"
    )
    public ResponseEntity<AdvanceTaxInvoiceResponseDto> createRequest(
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

    @PutMapping("/{requestId}/approve")
    @Operation(
            summary = "Approve Advance Tax Invoice request and generate Invoice"
    )
    public ResponseEntity<AdvanceTaxInvoiceResponseDto> approveRequest(
            @PathVariable Long requestId,
            @Valid
            @RequestBody
            AdvanceTaxInvoiceApprovalRequestDto requestDto
    ) {

        AdvanceTaxInvoiceResponseDto response =
                advanceTaxInvoiceService.approveRequest(
                        requestId,
                        requestDto
                        // i want same invoice feataute i
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{requestId}")
    @Operation(
            summary = "Get Advance Tax Invoice request by ID"
    )
    public ResponseEntity<AdvanceTaxInvoiceResponseDto> getRequestById(
            @PathVariable Long requestId
    ) {

        return ResponseEntity.ok(
                advanceTaxInvoiceService.getRequestById(
                        requestId
                )
        );
    }

    @GetMapping
    @Operation(
            summary = "List Advance Tax Invoice requests",
            description = """
                Accounts/Admin users can view all requests.
                Sales users can view only requests raised by themselves.
                """
    )
    public ResponseEntity<Page<AdvanceTaxInvoiceResponseDto>> getRequests(

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


    @PutMapping("/{invoiceId}/confirm-e-invoice-and-create-project")
    @Operation(
            summary = "Confirm/finalize Advance Tax Invoice and synchronize Operation Project"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice processed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failure"),
            @ApiResponse(responseCode = "403", description = "User not authorized"),
            @ApiResponse(responseCode = "404", description = "Advance Tax Invoice not found")
    })
    public ResponseEntity<ConfirmAdvanceInvoiceResponseDto>
    confirmEInvoiceAndCreateProject(
            @PathVariable Long invoiceId,
            @Valid @RequestBody ConfirmInvoiceEInvoiceRequestDto request
    ) {
        return ResponseEntity.ok(
                advanceTaxInvoiceService.confirmEInvoiceAndCreateProject(
                        invoiceId,
                        request
                )
        );
    }

}
