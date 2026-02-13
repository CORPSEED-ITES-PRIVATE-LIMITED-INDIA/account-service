package com.account.dto.dashboard;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstimateDashboardFilterRequest {

    private Long userId;

    private LocalDate fromDate;
    private LocalDate toDate;

    private String status;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    private Long companyId;
    private String companyName;

    private Long unitId;

    private String solutionType;

    private Boolean sentOnly; // true = only sent estimates
}
