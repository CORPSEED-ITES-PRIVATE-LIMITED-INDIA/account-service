// Final: Separate PaymentController (clean & MNC-style)
package com.account.controller.payement;

import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
import com.account.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
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

}