package com.account.dto.dashboard;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
public class RevenueByServiceItemDto {

    private Long solutionId;
    private String solutionName;

    private BigDecimal revenue;
    private Long invoiceCount;

    // For progress bar width
    private BigDecimal percentage;

    public RevenueByServiceItemDto(
            Long solutionId,
            String solutionName,
            BigDecimal revenue,
            Long invoiceCount
    ) {
        this.solutionId = solutionId;
        this.solutionName = solutionName;
        this.revenue = revenue == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : revenue.setScale(2, RoundingMode.HALF_UP);
        this.invoiceCount = invoiceCount == null ? 0L : invoiceCount;
        this.percentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}