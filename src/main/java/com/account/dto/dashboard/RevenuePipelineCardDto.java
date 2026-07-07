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
public class RevenuePipelineCardDto {

    private BigDecimal amount;
    private Long dealCount;
    private String label; // 12 deals expected

    public void normalize() {
        this.amount = amount == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : amount.setScale(2, RoundingMode.HALF_UP);

        this.dealCount = dealCount == null ? 0L : dealCount;
        this.label = this.dealCount + " deals expected";
    }
}