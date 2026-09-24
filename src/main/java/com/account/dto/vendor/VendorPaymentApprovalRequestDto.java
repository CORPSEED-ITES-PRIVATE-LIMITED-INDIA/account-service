package com.account.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Procurement vendor payment snapshot received from Operation Service.
 *
 * Operation Service calculates the procurement payment values.
 *
 * Account Service may:
 * 1. independently recalculate and cross-check them, or
 * 2. validate the immutable calculation snapshot before posting vouchers.
 *
 * Monetary model:
 *
 * taxableAmount / price
 *      = basic purchase amount before GST
 *
 * totalGstAmount
 *      = CGST + SGST + IGST
 *
 * invoiceGrossAmount
 *      = taxableAmount + totalGstAmount
 *
 * tdsAmount
 *      = TDS calculated on taxable/basic amount
 *
 * vendorNetPayableAmount
 *      = invoiceGrossAmount - tdsAmount
 *
 * bankPaymentAmount
 *      = actual vendor bank/cash payment
 *
 * settlementAmount
 *      = bankPaymentAmount + tdsAmount
 *
 * For current full-settlement workflow:
 *
 * bankPaymentAmount = vendorNetPayableAmount
 * settlementAmount  = invoiceGrossAmount
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPaymentApprovalRequestDto {

    /*
     * ================================================================
     * PROCUREMENT REFERENCES
     * ================================================================
     */

    /**
     * Procurement payment request ID from Operation Service.
     *
     * Used as voucher sourceId for idempotency.
     */
    private Long procurementPaymentRequestId;

    /**
     * Procurement order ID.
     */
    private Long procurementOrderId;

    /**
     * Human-readable PO number.
     *
     * Example:
     * PO-2026-000123
     */
    private String purchaseOrderNumber;


    /*
     * ================================================================
     * INVOICE INFORMATION
     * ================================================================
     */

    private String invoiceNumber;

    private LocalDate invoiceDate;


    /*
     * ================================================================
     * BASIC / TAXABLE VALUE
     * ================================================================
     */

    /**
     * Basic/taxable value before GST.
     *
     * Kept because existing Account implementation uses getPrice().
     */
    private BigDecimal price;

    /**
     * Same taxable/basic value represented explicitly
     * in the immutable calculation snapshot.
     *
     * Normally:
     *
     * taxableAmount == price
     */
    private BigDecimal taxableAmount;


    /*
     * ================================================================
     * GST CONFIGURATION
     * ================================================================
     */

    /**
     * Vendor registration type.
     *
     * Example:
     * REGISTERED
     * UNREGISTERED
     * SEZ
     * INTERNATIONAL
     */
    private String gstRegistrationType;

    /**
     * GST applicability.
     */
    private Boolean gstActive;

    /**
     * INTRA_STATE / INTER_STATE.
     */
    private String gstSupplyType;

    /**
     * Operation GST state code/reference.
     */
    private String gstStateCode;

    /**
     * GST percentage.
     *
     * Example:
     * 18.00
     */
    private BigDecimal gstPercentage;


    /*
     * ================================================================
     * GST CALCULATION SNAPSHOT
     * ================================================================
     */

    private BigDecimal cgstAmount;

    private BigDecimal sgstAmount;

    private BigDecimal igstAmount;

    /**
     * Total GST:
     *
     * CGST + SGST + IGST
     */
    private BigDecimal totalGstAmount;


    /*
     * ================================================================
     * INVOICE TOTAL
     * ================================================================
     */

    /**
     * GST-inclusive invoice amount.
     *
     * invoiceGrossAmount
     *      = taxableAmount + totalGstAmount
     */
    private BigDecimal invoiceGrossAmount;


    /*
     * ================================================================
     * TDS CONFIGURATION / CALCULATION
     * ================================================================
     */

    private Boolean tdsActive;

    /**
     * Amount on which TDS is calculated.
     *
     * Current procurement flow:
     *
     * tdsBaseAmount == taxableAmount
     */
    private BigDecimal tdsBaseAmount;

    /**
     * TDS percentage.
     *
     * Example:
     * 10.00
     */
    private BigDecimal tdsPercentage;

    /**
     * Calculated TDS amount.
     */
    private BigDecimal tdsAmount;

    /**
     * Normally null.
     *
     * Account Service should resolve its own
     * TDS_PAYABLE system ledger.
     */
    private Long tdsPayableLedgerId;


    /*
     * ================================================================
     * VENDOR PAYABLE
     * ================================================================
     */

    /**
     * Amount actually payable to vendor after TDS.
     *
     * vendorNetPayableAmount
     *      = invoiceGrossAmount - tdsAmount
     */
    private BigDecimal vendorNetPayableAmount;


    /*
     * ================================================================
     * SETTLEMENT
     * ================================================================
     */

    /**
     * Total invoice liability being settled.
     *
     * settlementAmount
     *      = bankPaymentAmount + tdsAmount
     *
     * For current full settlement:
     *
     * settlementAmount == invoiceGrossAmount
     */
    private BigDecimal settlementAmount;


    /*
     * ================================================================
     * PAYMENT EXECUTION
     * ================================================================
     */

    private LocalDate paymentDate;

    /**
     * Actual Bank/Cash amount released.
     *
     * Current full settlement:
     *
     * bankPaymentAmount
     *      = vendorNetPayableAmount
     */
    private BigDecimal bankPaymentAmount;

    /**
     * NEFT / RTGS / IMPS / UPI / CASH /
     * CHEQUE / BANK_TRANSFER etc.
     */
    private String paymentMode;

    /**
     * Paying BANK / CASH / PAYMENT_GATEWAY ledger.
     */
    private Long bankLedgerId;


    /*
     * ================================================================
     * OPTIONAL / LEGACY VENDOR LEDGER METADATA
     * ================================================================
     */

    /**
     * Normally null.
     *
     * Account Service resolves vendor ledger from
     * AccountVendorSyncRequestDto.operationVendorId.
     */
    private Long ledgerId;

    private String ledgerType;


    /*
     * ================================================================
     * TRANSACTION / PAYMENT PROOF
     * ================================================================
     */

    private String transactionReference;

    private String paymentProof;

    private List<String> proofAttachmentUrls;


    /*
     * ================================================================
     * APPROVAL AUDIT
     * ================================================================
     */

    private Long approvedByOperationUserId;

    private LocalDate approvedDate;

    private String approvalComment;


    /*
     * ================================================================
     * PAYMENT RELEASE AUDIT
     * ================================================================
     */

    private Long paymentReleasedByOperationUserId;

    private LocalDate paymentReleasedDate;

    private String releaseComment;


    /*
     * ================================================================
     * CALCULATION CONTRACT VERSION
     * ================================================================
     */

    /**
     * Optional calculation-contract version.
     *
     * Example:
     * PROCUREMENT_PAYMENT_V1
     */
    private String calculationVersion;
}