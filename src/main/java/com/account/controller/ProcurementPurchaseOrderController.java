package com.account.controller;

import com.account.dto.procurement.OperationApiResponseDto;
import com.account.service.ProcurementPurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accountService/api/procurement")
@RequiredArgsConstructor
public class ProcurementPurchaseOrderController {

    private final ProcurementPurchaseOrderService procurementPurchaseOrderService;

    @GetMapping
    public ResponseEntity<OperationApiResponseDto<?>> getProcurementPurchaseOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        OperationApiResponseDto<?> response =
                procurementPurchaseOrderService.getProcurementPurchaseOrders(
                        status,
                        page,
                        size
                );

        return ResponseEntity
                .status(resolveHttpStatus(response.getStatusCode()))
                .body(response);
    }

    @PutMapping("/{purchaseOrderId}/approve/{userId}")
    public ResponseEntity<OperationApiResponseDto<?>> approveProcurementPurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @PathVariable Long userId,
            @RequestParam(required = false) String comment
    ) {
        OperationApiResponseDto<?> response =
                procurementPurchaseOrderService.approveProcurementPurchaseOrder(
                        purchaseOrderId,
                        userId,
                        comment
                );

        return ResponseEntity
                .status(resolveHttpStatus(response.getStatusCode()))
                .body(response);
    }

    @PutMapping("/{purchaseOrderId}/reject/{userId}")
    public ResponseEntity<OperationApiResponseDto<?>> rejectProcurementPurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @PathVariable Long userId,
            @RequestParam String reason
    ) {
        OperationApiResponseDto<?> response =
                procurementPurchaseOrderService.rejectProcurementPurchaseOrder(
                        purchaseOrderId,
                        userId,
                        reason
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