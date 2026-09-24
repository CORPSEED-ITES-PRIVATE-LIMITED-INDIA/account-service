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
public class BillingOverviewCardDto {

    private BigDecimal value;
    private BigDecimal growthPercentage;
    private String growthDirection; // UP, DOWN, SAME
    private String comparisonLabel; // vs last month

    public void normalize() {
        this.value = value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);

        this.growthPercentage = growthPercentage == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : growthPercentage.setScale(2, RoundingMode.HALF_UP);

        if (this.growthDirection == null) {
            this.growthDirection = "SAME";
        }
    }
}