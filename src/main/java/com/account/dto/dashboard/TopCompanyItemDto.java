package com.account.dto.dashboard;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
public class TopCompanyItemDto {

    private Long companyId;
    private String companyName;

    private BigDecimal totalRevenue;
    private Long invoiceCount;

    public TopCompanyItemDto(
            Long companyId,
            String companyName,
            BigDecimal totalRevenue,
            Long invoiceCount
    ) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.totalRevenue = totalRevenue == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : totalRevenue.setScale(2, RoundingMode.HALF_UP);
        this.invoiceCount = invoiceCount == null ? 0L : invoiceCount;
    }
}