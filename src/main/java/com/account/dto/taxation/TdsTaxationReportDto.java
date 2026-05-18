package com.account.dto.taxation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TdsTaxationReportDto {

    private Long totalTdsRegistrations;

    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTdsAmount;

    private BigDecimal pendingTdsAmount;
    private BigDecimal approvedTdsAmount;

    private Long pendingTdsCount;
    private Long approvedTdsCount;

    private BigDecimal averageTdsAmount;
}