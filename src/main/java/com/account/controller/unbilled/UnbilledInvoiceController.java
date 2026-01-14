package com.account.controller.unbilled;

;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;
import com.account.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Unbilled Invoices", description = "APIs for managing unbilled invoices (approval by accounts)")
@RestController
@RequestMapping("/api/v1/unbilled-invoices")
@RequiredArgsConstructor
public class UnbilledInvoiceController {

    private final PaymentService paymentService;  // or inject UnbilledInvoiceService if you create one

    @Operation(summary = "Approve unbilled invoice (Accounts team only)",
            description = "Changes status to APPROVED and generates tax invoice")
    @PostMapping("/{unbilledId}/approve")
    public ResponseEntity<UnbilledInvoiceApprovalResponseDto> approveUnbilledInvoice(
            @PathVariable Long unbilledId,
            @Valid @RequestBody UnbilledInvoiceApprovalRequestDto request) {

        UnbilledInvoiceApprovalResponseDto response =
                paymentService.approveUnbilledInvoice(unbilledId, request);

        return ResponseEntity.ok(response);
    }

    // Optional: Add reject endpoint later
    // @PostMapping("/{unbilledId}/reject")
}