package com.account.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class BillingCollectionTrendResponseDto {

    private Long userId;
    private String groupBy;

    private LocalDate fromDate;
    private LocalDate toDate;

    private BigDecimal totalBilled;
    private BigDecimal totalCollected;

    private List<BillingCollectionTrendPointDto> points;
}