package com.account.service;

import com.account.dto.operationService.ProcurementPaymentActionRequestDto;
import com.account.dto.procurement.OperationApiResponseDto;

import java.util.Map;

public interface ProcurementPaymentRequestService {

    OperationApiResponseDto<?> getProcurementPaymentRequests(
            String status,
            int page,
            int size
    );

    OperationApiResponseDto<?> approveProcurementPaymentRequest(
            Long paymentRequestId,
            Long userId,
            String comment
    );

    OperationApiResponseDto<?> rejectProcurementPaymentRequest(
            Long paymentRequestId,
            Long userId,
            String reason
    );

    OperationApiResponseDto<?> releaseProcurementPayment(
            Long paymentRequestId,
            Long userId,
            ProcurementPaymentActionRequestDto request
    );


    Map<String, Object> getApprovedOrReleasedPayments(
            int page,
            int size
    );


}