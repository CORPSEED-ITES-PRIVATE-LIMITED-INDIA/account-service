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
    private String state;
    private String city;
    private String country;
    private String primaryPinCode;
    private String onboardingStatus;
}