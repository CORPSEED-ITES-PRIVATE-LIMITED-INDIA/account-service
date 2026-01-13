package com.account.dto.estimate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanySummaryDto {
    private Long id;
    private String name;
    private String panNo;
    private String gstNo;
    private String gstType;
    private String state;           // very useful for GST logic
    private String city;
    private String primaryPinCode;
    private String onboardingStatus;
    private Boolean accountsApproved;
}