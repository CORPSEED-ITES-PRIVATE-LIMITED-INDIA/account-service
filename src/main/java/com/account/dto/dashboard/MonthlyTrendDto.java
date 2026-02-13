package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;



@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyTrendDto {

    private String month; // 2026-01
    private Long count;
    private BigDecimal revenue;
}
