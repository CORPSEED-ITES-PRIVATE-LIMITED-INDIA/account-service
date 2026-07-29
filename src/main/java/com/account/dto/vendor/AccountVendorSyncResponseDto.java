package com.account.dto.vendor;

import lombok.*;

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

    private Long ledgerId;

    private String ledgerCode;

    private String ledgerName;

    private String ledgerType;

    private Long ledgerGroupId;

    private String ledgerGroupName;

    private String ledgerGroupType;

    /*
     * CREATED
     * UPDATED
     */
    private String action;

    private Boolean active;

    private String syncStatus;

    private LocalDateTime syncedAt;

    private String message;
}