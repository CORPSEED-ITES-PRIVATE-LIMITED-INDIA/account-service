package com.account.dto.estimate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUnitSummaryDto {

    private Long id;
    private String unitName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pinCode;
    private String gstNo;

    private String gstRegistrationType;

    private String status;
    private String onboardingStatus;
}