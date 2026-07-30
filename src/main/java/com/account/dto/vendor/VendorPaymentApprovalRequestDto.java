package com.account.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPaymentApprovalRequestDto {

    private Long procurementPaymentRequestId;

    private Long procurementOrderId;

    private String purchaseOrderNumber;

    private String invoiceNumber;

    private LocalDate invoiceDate;

    /*
     * Basic amount before GST.
     */
    private BigDecimal price;

    /*
     * GST registration category:
     *
     * REGISTERED
     * UNREGISTERED
     * SEZ
     * INTERNATIONAL
     */
    private String gstRegistrationType;

    /*
     * Determines CGST/SGST versus IGST.
     *
     * INTRA_STATE
     * INTER_STATE
     *
     * For SEZ and INTERNATIONAL, Account Service
     * treats the transaction as zero-rated.
     */
    private String gstSupplyType;

    private String gstStateCode;

    private BigDecimal gstPercentage;

    private Boolean tdsActive;

    private BigDecimal tdsPercentage;

    private Long approvedByOperationUserId;

    private LocalDate approvedDate;

    private String approvalComment;
}