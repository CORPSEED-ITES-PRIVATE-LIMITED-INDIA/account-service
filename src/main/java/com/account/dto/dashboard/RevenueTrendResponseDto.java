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
public class RevenueTrendResponseDto {

    private Long userId;
    private String groupBy; // MONTHLY
    private LocalDate fromDate;
    private LocalDate toDate;

    private BigDecimal totalRevenue;
    private List<RevenueTrendPointDto> points;
}