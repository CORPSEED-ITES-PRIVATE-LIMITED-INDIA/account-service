// Final: Separate PaymentController (clean & MNC-style)
package com.account.controller.payement;

import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
import com.account.service.PaymentService;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments", description = "APIs for payment registration and management")
@RestController
@RequestMapping("/accountService/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/register")
    @Operation(summary = "Register customer payment (Salesperson use)")
    public ResponseEntity<PaymentRegistrationResponseDto> registerPayment(
            @Valid @RequestBody PaymentRegistrationRequestDto request,
            @RequestParam("userId") Long salespersonUserId) {
        PaymentRegistrationResponseDto response = paymentService.registerPayment(request, salespersonUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @PostMapping("/unbilled/{unbilledId}/reject")
    @Operation(summary = "Reject an unbilled invoice (Accounts use)",
            description = "Rejects the unbilled if it's PENDING_APPROVAL. " +
                    "Pass rejectionReason and approverUserId as query params.")
    public ResponseEntity<String> rejectUnbilled(
            @PathVariable @Parameter(description = "Unbilled invoice ID") Long unbilledId,
            @RequestParam @Parameter(description = "Reason for rejection (required)", required = true) String rejectionReason,
            @RequestParam @Parameter(description = "ID of the accounts user rejecting", required = true) Long approverUserId) {

        // Basic validation (since no @Valid on params)
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Rejection reason is required");
        }

        paymentService.rejectUnbilledInvoice(unbilledId, rejectionReason.trim(), approverUserId);

        return ResponseEntity.ok("Unbilled invoice rejected successfully. Reason: " + rejectionReason.trim());
    }

}