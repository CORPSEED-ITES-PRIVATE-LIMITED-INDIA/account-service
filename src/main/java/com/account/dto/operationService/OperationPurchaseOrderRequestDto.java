package com.account.dto.operationService;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OperationPurchaseOrderRequestDto {

    private Long procurementAssignmentId;
    private Long vendorId;

    private String poReferenceNumber;

    private BigDecimal estimatedAmount;
    private BigDecimal finalAmount;

    private BigDecimal gstRate;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;

    private BigDecimal totalTaxAmount;
    private BigDecimal grandTotal;

    private String scopeOfWork;
    private String paymentTerms;
    private String termsAndConditions;
    private String remarks;

    private List<String> attachmentUrls;

    private String paymentTypeName;

    private Long updatedBy;
}