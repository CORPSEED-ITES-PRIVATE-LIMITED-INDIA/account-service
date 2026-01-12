package com.account.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class CompanyRequestDto {


    @NotNull(message = "leadCompanyId is required to maintain same ID in account service")
    private Long leadCompanyId;

    // === BASIC COMPANY INFO (MANDATORY) ===
    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$", message = "Invalid PAN format. Must be like ABCDE1234F")
    private String panNo;

    // === OPTIONAL COMPANY DETAILS ===
    private String rating;
    private String companyAge;
    private Date establishDate;
    private String industry; // Legacy free-text field

    // === INDUSTRY HIERARCHY ===
    private Long industryId;
    private Long subIndustryId;
    private Long subSubIndustryId;

    // === LEGACY ADDRESSES (Kept for backward compatibility – now optional) ===
    private String address;          // Primary HQ address line
    private String city;
    private String state;
    private String country;
    private String primaryPinCode;

    private String sAddress;         // Secondary address
    private String sCity;
    private String sState;
    private String sCountry;
    private String secondaryPinCode;

    // === SALES & ASSIGNMENT ===
    private Long assigneeId;
    private String stage;            // e.g., New, Working, Proposal, Closed Won
    private String status;           // e.g., Active, Inactive

    private Long leadId;             // Optional: link to originating lead

    // === AGREEMENTS & PAYMENT ===
    private String paymentTerm;      // e.g., Net 30, Advance
    private Boolean aggrementPresent;
    private String aggrement;
    private String nda;
    private Boolean ndaPresent;
    private String revenue;          // e.g., "500 Crores", "85 Lakhs"

    // === CONSULTANT FLOW ===
    private Boolean isConsultant = false;        // true if this is a consultant company

    // If isConsultant = true → this links to the actual client company
    // If isConsultant = false → this can be used to link to the consultant who brought the client
    private Long actualClientCompanyId;

    // === MULTI-LOCATION SUPPORT: UNITS / BRANCHES / OUTLETS ===
    // This is the modern way — add multiple units in one request
    private List<CompanyUnitRequestDto> units = new ArrayList<>();

    // === AUDIT ===
    private Long createdById;
    private Long updatedById;
}