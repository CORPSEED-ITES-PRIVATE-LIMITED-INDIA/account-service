package com.account.serviceImpl;

import com.account.dto.operationService.ProcurementPurchaseOrderActionRequestDto;
import com.account.dto.procurement.OperationApiResponseDto;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.service.ProcurementPurchaseOrderService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProcurementPurchaseOrderServiceImpl implements ProcurementPurchaseOrderService {

    private final OperationFeignClient operationFeignClient;

    @Override
    @Transactional(readOnly = true)
    public OperationApiResponseDto<?> getProcurementPurchaseOrders(
            String status,
            int page,
            int size
    ) {
        try {
            ResponseEntity<?> response = operationFeignClient.getProcurementPurchaseOrders(
                    status,
                    page,
                    size
            );

            return OperationApiResponseDto.builder()
                    .success(true)
                    .message("Procurement purchase orders fetched successfully")
                    .statusCode(response.getStatusCode().value())
                    .data(response.getBody())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (FeignException e) {
            return buildFeignErrorResponse(e);
        } catch (Exception e) {
            return buildInternalErrorResponse(e);
        }
    }

    @Override
    @Transactional
    public OperationApiResponseDto<?> approveProcurementPurchaseOrder(
            Long purchaseOrderId,
            Long userId,
            String comment
    ) {
        validatePurchaseOrderAndUser(purchaseOrderId, userId);

        try {
            ProcurementPurchaseOrderActionRequestDto request =
                    ProcurementPurchaseOrderActionRequestDto.builder()
                            .comment(comment)
                            .build();

            ResponseEntity<?> response = operationFeignClient.approveProcurementPurchaseOrder(
                    purchaseOrderId,
                    userId,
                    request
            );

            return OperationApiResponseDto.builder()
                    .success(true)
                    .message("Procurement purchase order approved successfully")
                    .statusCode(response.getStatusCode().value())
                    .data(response.getBody())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (FeignException e) {
            return buildFeignErrorResponse(e);
        } catch (Exception e) {
            return buildInternalErrorResponse(e);
        }
    }

    @Override
    @Transactional
    public OperationApiResponseDto<?> rejectProcurementPurchaseOrder(
            Long purchaseOrderId,
            Long userId,
            String reason
    ) {
        validatePurchaseOrderAndUser(purchaseOrderId, userId);

        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException(
                    "Rejection reason is required",
                    "ERR_REJECTION_REASON_REQUIRED",
                    "reason"
            );
        }

        try {
            ProcurementPurchaseOrderActionRequestDto request =
                    ProcurementPurchaseOrderActionRequestDto.builder()
                            .reason(reason.trim())
                            .build();

            ResponseEntity<?> response = operationFeignClient.rejectProcurementPurchaseOrder(
                    purchaseOrderId,
                    userId,
                    request
            );

            return OperationApiResponseDto.builder()
                    .success(true)
                    .message("Procurement purchase order rejected successfully")
                    .statusCode(response.getStatusCode().value())
                    .data(response.getBody())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (FeignException e) {
            return buildFeignErrorResponse(e);
        } catch (Exception e) {
            return buildInternalErrorResponse(e);
        }
    }

    private void validatePurchaseOrderAndUser(Long purchaseOrderId, Long userId) {
        if (purchaseOrderId == null) {
            throw new ValidationException(
                    "Purchase order id is required",
                    "ERR_PURCHASE_ORDER_ID_REQUIRED",
                    "purchaseOrderId"
            );
        }

        if (userId == null) {
            throw new ValidationException(
                    "User id is required",
                    "ERR_USER_ID_REQUIRED",
                    "userId"
            );
        }
    }

    private OperationApiResponseDto<?> buildFeignErrorResponse(FeignException e) {
        String errorBody = e.contentUTF8();

        String message = errorBody != null && !errorBody.trim().isEmpty()
                ? errorBody
                : "Operation service error occurred";

        return OperationApiResponseDto.builder()
                .success(false)
                .message(message)
                .statusCode(e.status())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private OperationApiResponseDto<?> buildInternalErrorResponse(Exception e) {
        return OperationApiResponseDto.builder()
                .success(false)
                .message(e.getMessage() != null ? e.getMessage() : "Internal server error")
                .statusCode(500)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }
}