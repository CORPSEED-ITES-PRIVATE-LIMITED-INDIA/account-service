package com.account.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Immutable procurement invoice and vendor-payment calculation snapshot
 * received from Operation Service.
 *
 * Amount meanings:
 *
 * price / taxableAmount:
 *     Basic taxable purchase value before GST.
 *
 * invoiceGrossAmount:
 *     taxableAmount + totalGstAmount.
 *
 * vendorNetPayableAmount:
 *     invoiceGrossAmount - tdsAmount.
 *
 * settlementAmount:
 *     bankPaymentAmount + tdsAmount.
 *
 * For a full payment:
 *     vendorNetPayableAmount == bankPaymentAmount
 *     settlementAmount == invoiceGrossAmount
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPaymentApprovalRequestDto {

    /*
     * Procurement references
     */
    private Long procurementPaymentRequestId;
    private Long procurementOrderId;
    private String purchaseOrderNumber;

    /*
     * Vendor invoice
     */
    private String invoiceNumber;
    private LocalDate invoiceDate;

    /**
     * Legacy/basic purchase value.
     *
     * Keep this field because the current Operation Service payload
     * sends the taxable value as price.
     */
    private BigDecimal price;

    /**
     * Taxable/basic purchase value before GST.
     *
     * This should contain the same value as price.
     */
    private BigDecimal taxableAmount;

    /**
     * Total invoice value including GST.
     *
     * invoiceGrossAmount = taxableAmount + totalGstAmount
     */
    private BigDecimal invoiceGrossAmount;

    /*
     * GST snapshot
     */
    private Boolean gstActive;
    private String gstRegistrationType;

    /**
     * INTRA_STATE or INTER_STATE when GST is active.
     */
    private String gstSupplyType;

    private String gstStateCode;
    private BigDecimal gstPercentage;

    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalGstAmount;

    /*
     * TDS snapshot
     */
    private Boolean tdsActive;
    private BigDecimal tdsPercentage;
    private BigDecimal tdsAmount;

    /**
     * Amount payable to the vendor after deducting TDS.
     *
     * vendorNetPayableAmount = invoiceGrossAmount - tdsAmount
     */
    private BigDecimal vendorNetPayableAmount;

    /**
     * Total amount settled against the vendor invoice.
     *
     * settlementAmount = bankPaymentAmount + tdsAmount
     */
    private BigDecimal settlementAmount;

    /*
     * Approval information
     */
    private Long approvedByOperationUserId;
    private LocalDate approvedDate;
    private String approvalComment;

    /**
     * Optional legacy value.
     * Account Service should resolve the system TDS ledger itself.
     */
    private Long tdsPayableLedgerId;

    /*
     * Payment release
     */
    private LocalDate paymentDate;
    private BigDecimal bankPaymentAmount;
    private String paymentMode;
    private Long bankLedgerId;

    /**
     * Optional legacy vendor-ledger metadata.
     * Account Service resolves the vendor ledger itself.
     */
    private Long ledgerId;
    private String ledgerType;

    private String transactionReference;
    private String paymentProof;
    private List<String> proofAttachmentUrls;

    private Long paymentReleasedByOperationUserId;
    private LocalDate paymentReleasedDate;
    private String releaseComment;
}