package com.account.dto.dashboard;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
public class TopSellingServiceItemDto {

    private Long solutionId;
    private String solutionName;

    // For UI label: "12 Leads"
    private Long leadCount;

    // Actual generated invoice count
    private Long invoiceCount;

    // For UI amount: ₹ 2,40,000
    private BigDecimal totalRevenue;

    public TopSellingServiceItemDto(
            Long solutionId,
            String solutionName,
            Long leadCount,
            Long invoiceCount,
            BigDecimal totalRevenue
    ) {
        this.solutionId = solutionId;
        this.solutionName = solutionName;
        this.leadCount = leadCount == null ? 0L : leadCount;
        this.invoiceCount = invoiceCount == null ? 0L : invoiceCount;
        this.totalRevenue = totalRevenue == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalRevenue.setScale(2, RoundingMode.HALF_UP);
    }
}