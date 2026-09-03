package com.account.controller.payement;

import com.account.dto.payment.PaymentLegalSummaryResponseDto;
import com.account.dto.payment.PaymentLegalVerificationResponseDto;
import com.account.dto.payment.ReviewPaymentLegalVerificationRequestDto;
import com.account.service.PaymentLegalVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accountService/api/v1/payment-legal-verification")
@RequiredArgsConstructor
public class PaymentLegalVerificationController {

    private final PaymentLegalVerificationService paymentLegalVerificationService;

    @GetMapping("/pending")
    public ResponseEntity<List<PaymentLegalVerificationResponseDto>> getPendingRequests(
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(
                paymentLegalVerificationService.getPendingRequests(userId)
        );
    }

    @GetMapping("/unbilled/{unbilledId}")
    public ResponseEntity<List<PaymentLegalVerificationResponseDto>> getRequestsByUnbilled(
            @PathVariable Long unbilledId
    ) {
        return ResponseEntity.ok(
                paymentLegalVerificationService.getRequestsByUnbilled(unbilledId)
        );
    }

    @PutMapping("/{requestId}/review")
    public ResponseEntity<PaymentLegalVerificationResponseDto> reviewRequest(
            @PathVariable Long requestId,
            @RequestParam Long reviewedBy,
            @Valid @RequestBody ReviewPaymentLegalVerificationRequestDto request
    ) {
        return ResponseEntity.ok(
                paymentLegalVerificationService.reviewRequest(
                        requestId,
                        reviewedBy,
                        request
                )
        );
    }
    @GetMapping("/summary")
    public ResponseEntity<PaymentLegalSummaryResponseDto> getSummary(
            @RequestParam Long userId) {
        return ResponseEntity.ok(paymentLegalVerificationService.getSummary(userId));
    }
}