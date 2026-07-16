package com.account.tds;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TdsCollectionSummaryDto {

    /**
     * Total active client TDS:
     *
     * PENDING + APPROVED
     */
    private BigDecimal totalTdsAmount;

    /**
     * TDS registered by salesperson but not yet
     * approved by Accounts.
     */
    private BigDecimal pendingAmount;

    /**
     * Current temporary definition:
     *
     * APPROVED TDS is treated as claimed TDS.
     *
     * For actual Form 26AS/AIS reconciliation,
     * add CLAIMED/RECONCILED statuses separately.
     */
    private BigDecimal claimedAmount;

    private Long totalCount;

    private Long pendingCount;

    private Long claimedCount;
}
