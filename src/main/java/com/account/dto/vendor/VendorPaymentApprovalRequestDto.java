package com.account.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Commercial and payment data sent from Operation Service to Account Service.
 *
 * Account Service calculates GST/TDS and creates:
 * 1. PURCHASE_INVOICE voucher
 * 2. PAYMENT voucher, when payment release fields are present
 */
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

    /** Taxable/basic purchase value before GST. */
    private BigDecimal price;

    /** Explicit GST applicability flag. */
    private Boolean gstActive;

    private String gstRegistrationType;

    /** INTRA_STATE or INTER_STATE when gstActive is true. */
    private String gstSupplyType;
    private String gstStateCode;
    private BigDecimal gstPercentage;

    private Boolean tdsActive;
    private BigDecimal tdsPercentage;

    private Long approvedByOperationUserId;
    private LocalDate approvedDate;
    private String approvalComment;

    /** Optional cross-check value. Account Service recalculates TDS. */
    private BigDecimal tdsAmount;

    /** Optional legacy value. Account Service resolves its system TDS ledger. */
    private Long tdsPayableLedgerId;

    private LocalDate paymentDate;
    private BigDecimal bankPaymentAmount;
    private String paymentMode;
    private Long bankLedgerId;

    /** Optional legacy metadata. Account Service resolves vendor ledger itself. */
    private Long ledgerId;
    private String ledgerType;

    private String transactionReference;
    private String paymentProof;
    private List<String> proofAttachmentUrls;

    private Long paymentReleasedByOperationUserId;
    private LocalDate paymentReleasedDate;
    private String releaseComment;
}