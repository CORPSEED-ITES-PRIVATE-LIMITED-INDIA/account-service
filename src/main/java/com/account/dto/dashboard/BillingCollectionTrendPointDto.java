package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillingCollectionTrendPointDto {

    private String month;        // 2026-07
    private String label;        // Jul
    private BigDecimal billed;   // Unbilled total amount
    private BigDecimal collected; // Approved payment amount
}