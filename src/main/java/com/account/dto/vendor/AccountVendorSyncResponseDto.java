package com.account.dto.vendor;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVendorSyncResponseDto {

    // Vendor

    private Long externalVendorId;

    private Long operationVendorId;

    private Long vendorAccountsSubmissionId;

    private Long vendorFinalizationId;

    private String vendorName;

    // Ledger

    private Long ledgerId;

    private String ledgerCode;

    private String ledgerName;

    private String ledgerType;

    private Long ledgerGroupId;

    private String ledgerGroupName;

    private String ledgerGroupType;

    private String action;

    private Boolean active;

    // Voucher

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

    // Sync result

    private String syncStatus;

    private LocalDateTime syncedAt;

    private String message;
}