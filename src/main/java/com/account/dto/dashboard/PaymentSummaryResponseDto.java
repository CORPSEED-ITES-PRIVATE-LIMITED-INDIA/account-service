package com.account.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
public class PaymentSummaryResponseDto {

    private Long userId;
    private String period;
    private LocalDate fromDate;
    private LocalDate toDate;

    private BigDecimal totalBilled;
    private BigDecimal received;
    private BigDecimal pending;
    private BigDecimal collectionPercentage;
}