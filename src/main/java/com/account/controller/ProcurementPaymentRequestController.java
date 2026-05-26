package com.account.controller;

import com.account.dto.operationService.ProcurementPaymentActionRequestDto;
import com.account.dto.procurement.OperationApiResponseDto;
import com.account.service.ProcurementPaymentRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accountService/api/procurement-payment-requests")
@RequiredArgsConstructor
public class ProcurementPaymentRequestController {

    private final ProcurementPaymentRequestService procurementPaymentRequestService;

    @GetMapping
    public ResponseEntity<OperationApiResponseDto<?>> getProcurementPaymentRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        OperationApiResponseDto<?> response =
                procurementPaymentRequestService.getProcurementPaymentRequests(
                        status,
                        page,
                        size
                );

        return ResponseEntity
                .status(resolveHttpStatus(response.getStatusCode()))
                .body(response);
    }

    @PutMapping("/{paymentRequestId}/approve/{userId}")
    public ResponseEntity<OperationApiResponseDto<?>> approveProcurementPaymentRequest(
            @PathVariable Long paymentRequestId,
            @PathVariable Long userId,
            @RequestParam(required = false) String comment
    ) {
        OperationApiResponseDto<?> response =
                procurementPaymentRequestService.approveProcurementPaymentRequest(
                        paymentRequestId,
                        userId,
                        comment
                );

        return ResponseEntity
                .status(resolveHttpStatus(response.getStatusCode()))
                .body(response);
    }

    @PutMapping("/{paymentRequestId}/reject/{userId}")
    public ResponseEntity<OperationApiResponseDto<?>> rejectProcurementPaymentRequest(
            @PathVariable Long paymentRequestId,
            @PathVariable Long userId,
            @RequestParam String reason
    ) {
        OperationApiResponseDto<?> response =
                procurementPaymentRequestService.rejectProcurementPaymentRequest(
                        paymentRequestId,
                        userId,
                        reason
                );

        return ResponseEntity
                .status(resolveHttpStatus(response.getStatusCode()))
                .body(response);
    }

    @PutMapping("/{paymentRequestId}/release-payment/{userId}")
    public ResponseEntity<OperationApiResponseDto<?>> releaseProcurementPayment(
            @PathVariable Long paymentRequestId,
            @PathVariable Long userId,
            @RequestBody ProcurementPaymentActionRequestDto request
    ) {
        OperationApiResponseDto<?> response =
                procurementPaymentRequestService.releaseProcurementPayment(
                        paymentRequestId,
                        userId,
                        request
                );

        return ResponseEntity
                .status(resolveHttpStatus(response.getStatusCode()))
                .body(response);
    }

    private HttpStatus resolveHttpStatus(Integer statusCode) {
        if (statusCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        try {
            return HttpStatus.valueOf(statusCode);
        } catch (Exception e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}