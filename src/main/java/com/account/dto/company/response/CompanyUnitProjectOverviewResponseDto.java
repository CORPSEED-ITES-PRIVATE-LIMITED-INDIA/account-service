package com.account.dto.company.response;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CompanyUnitProjectOverviewResponseDto {
    private Long companyId;
    private String companyName;
    private String panNo;
    private String onboardingStatus;

    private List<CompanyUnitOverviewDto> companyUnits;
}