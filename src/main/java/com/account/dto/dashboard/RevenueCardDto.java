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
public class RevenueCardDto {

    private BigDecimal amount;
    private BigDecimal growthPercentage;
    private String growthDirection; // UP, DOWN, SAME
    private String comparisonLabel; // vs Apr, vs last week

    public void normalize() {
        this.amount = amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);

        this.growthPercentage = growthPercentage == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : growthPercentage.setScale(2, RoundingMode.HALF_UP);
    }
}