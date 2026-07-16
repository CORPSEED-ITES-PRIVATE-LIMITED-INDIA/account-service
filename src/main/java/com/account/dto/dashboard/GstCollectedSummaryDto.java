package com.account.dto.dashboard;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class GstCollectedSummaryDto {

    private BigDecimal taxableAmount;
    private BigDecimal gstAmount;
    private BigDecimal pendingAmount;
    private BigDecimal filedAmount;
    private BigDecimal reconciledAmount;

    public GstCollectedSummaryDto(
            BigDecimal taxableAmount,
            BigDecimal gstAmount,
            BigDecimal pendingAmount,
            BigDecimal filedAmount,
            BigDecimal reconciledAmount
    ) {
        this.taxableAmount = zeroIfNull(taxableAmount);
        this.gstAmount = zeroIfNull(gstAmount);
        this.pendingAmount = zeroIfNull(pendingAmount);
        this.filedAmount = zeroIfNull(filedAmount);
        this.reconciledAmount = zeroIfNull(reconciledAmount);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null
                ? value
                : BigDecimal.ZERO;
    }
}