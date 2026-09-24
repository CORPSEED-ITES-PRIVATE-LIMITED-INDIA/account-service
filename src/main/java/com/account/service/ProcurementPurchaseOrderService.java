package com.account.service;

import com.account.dto.procurement.OperationApiResponseDto;

public interface ProcurementPurchaseOrderService {

    OperationApiResponseDto<?> getProcurementPurchaseOrders(
            String status,
            int page,
            int size
    );

    OperationApiResponseDto<?> approveProcurementPurchaseOrder(
            Long purchaseOrderId,
            Long userId,
            String comment
    );

    OperationApiResponseDto<?> rejectProcurementPurchaseOrder(
            Long purchaseOrderId,
            Long userId,
            String reason
    );
}