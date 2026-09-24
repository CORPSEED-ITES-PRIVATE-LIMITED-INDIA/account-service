package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RevenueTrendPointDto {

    private String monthKey;     // 2026-07
    private String label;        // Jul '26
    private BigDecimal revenue;  // 720000.00

    public void normalize() {
        this.revenue = revenue == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : revenue.setScale(2, RoundingMode.HALF_UP);
    }
}