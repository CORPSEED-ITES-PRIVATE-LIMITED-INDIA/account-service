package com.account.dto.company;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class CompanyResponseDto {

    private Long id;
    private String name;
    private String panNo;

    private String address;
    private String city;
    private String state;
    private String country;
    private String primaryPinCode;

    private String sAddress;
    private String sCity;
    private String sState;
    private String sCountry;
    private String secondaryPinCode;

    private String onboardingStatus;
    private boolean accountsApproved;
    private String accountsRemark;

    private Date createDate;
    private Date updateDate;

    private List<CompanyUnitResponseDto> units = new ArrayList<>();
}