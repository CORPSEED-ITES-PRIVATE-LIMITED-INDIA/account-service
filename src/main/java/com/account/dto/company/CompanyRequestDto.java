package com.account.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CompanyRequestDto {

    @NotNull(message = "leadCompanyId is required to maintain same ID in account service")
    private Long leadCompanyId;

    @NotBlank(message = "Company name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 10)
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

    private String companyAge;
    private java.util.Date establishDate;
    private String industry;

    private Long assigneeId;
    private String stage;
    private String status;

    private Boolean isConsultant;
    private Long parentId;

    private String paymentTerm;
    private Boolean aggrementPresent;
    private String aggrement;
    private String nda;
    private Boolean ndaPresent;
    private String revenue;

    private List<CompanyUnitRequestDto> units = new ArrayList<>();

    private Long createdById;
    private Long updatedById;
}