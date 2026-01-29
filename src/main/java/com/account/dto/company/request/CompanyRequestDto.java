package com.account.dto.company.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CompanyRequestDto {


    @NotNull(message = "leadCompanyId is required to maintain mapping with lead service")
    private Long leadCompanyId;



    @NotBlank(message = "Company name is required")
    private String name;

    @NotBlank(message = "PAN is required for full registration")
    private String panNo;

    private String rating;
    private String companyAge;
    private LocalDate establishDate;

    private String address;
    private String city;
    private String state;
    private String country;
    private String primaryPinCode;

    // Industry (you can use IDs or names/strings – here using strings for simplicity)
    private String industry;
    private String subIndustry;
    private String subSubIndustry;

    private Boolean isConsultant;

    // Agreements & financials
    private String paymentTerm;
    private Boolean aggrementPresent;
    private String aggrement;
    private Boolean ndaPresent;
    private String nda;
    private String revenue;

    // Units – can update existing or add new
    @Valid
    private List<CompanyUnitFullRequestDto> units;

    @NotNull(message = "updatedBy user ID is required")
    private Long updatedById;
}