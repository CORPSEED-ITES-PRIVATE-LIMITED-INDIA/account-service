package com.account.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVendorSyncResponseDto {

    private Long externalVendorId;
    private Long operationVendorId;
    private Long vendorAccountsSubmissionId;
    private Long vendorFinalizationId;
    private String vendorName;

    private Long ledgerId;
    private String ledgerCode;
    private String ledgerName;
    private String ledgerType;

    private Long ledgerGroupId;
    private String ledgerGroupName;
    private String ledgerGroupType;

    private String action;
    private Boolean active;

    private BigDecimal price;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalGstAmount;
    private BigDecimal grossInvoiceAmount;
    private BigDecimal tdsAmount;
    private BigDecimal vendorNetPayableAmount;

    private Long purchaseLedgerId;
    private Long inputCgstLedgerId;
    private Long inputSgstLedgerId;
    private Long inputIgstLedgerId;
    private Long tdsPayableLedgerId;

    /* Legacy fields: PURCHASE_INVOICE voucher. */
    private Boolean voucherCreated;
    private Long voucherId;
    private String voucherNumber;
    private String voucherType;
    private String voucherSourceType;
    private Long voucherSourceId;
    private LocalDate voucherDate;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String voucherStatus;

    /* PAYMENT voucher created on vendor payment release. */
    private Boolean paymentVoucherCreated;
    private Long paymentVoucherId;
    private String paymentVoucherNumber;
    private LocalDate paymentVoucherDate;
    private BigDecimal paymentVoucherTotalDebit;
    private BigDecimal paymentVoucherTotalCredit;
    private String paymentVoucherStatus;
    private Long paymentBankLedgerId;

    private String syncStatus;
    private LocalDateTime syncedAt;
    private String message;
}