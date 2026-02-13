package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyRevenueDto {

    private String companyName;
    private Long estimateCount;
    private BigDecimal revenue;
}
