package com.account.dto.company.response;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CompanyUnitOverviewDto {
    private Long unitId;
    private String unitName;
    private String city;
    private String state;
    private String gstNo;
    private String onboardingStatus;

    private List<UnitBusinessRecordDto> records;
}