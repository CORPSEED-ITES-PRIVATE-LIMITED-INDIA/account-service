package com.account.dto.company;

import jakarta.validation.Valid;
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

    /**
     * Lead/external system identifier.
     * NOTE: If your Company.id is @GeneratedValue, do NOT try to save this into Company.id.
     * Store it in Company.leadCompanyId (separate column).
     */
    @NotNull(message = "leadCompanyId is required to maintain mapping with lead service")
    private Long leadCompanyId;

    // ─────────────────────────────
    // BASIC COMPANY INFO (MANDATORY)
    // ─────────────────────────────
    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name cannot exceed 255 characters")
    private String name;

    @NotBlank(message = "PAN number is required")
    @Pattern(
            regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$",
            message = "Invalid PAN format. Must be like ABCDE1234F"
    )
    private String panNo;

    // ─────────────────────────────
    // OPTIONAL COMPANY DETAILS
    // ─────────────────────────────
    private Date establishDate;

    @Size(max = 255, message = "Industry cannot exceed 255 characters")
    private String industry;

    // If you use master tables (optional)
    private Long industryId;
    private Long subIndustryId;
    private Long subSubIndustryId;

    // Rating/age are optional; keep only if you use them (otherwise remove)
    private String rating;
    private String companyAge;

    // ─────────────────────────────
    // SALES & ASSIGNMENT (optional)
    // ─────────────────────────────
    private Long assigneeId;
    private String status;  // Active, Inactive
    private Long leadId;

    // ─────────────────────────────
    // AGREEMENTS & PAYMENT (company-level)
    // ─────────────────────────────
    private String paymentTerm;
    private Boolean aggrementPresent;
    private String aggrement;
    private String nda;
    private Boolean ndaPresent;
    private String revenue;

    // ─────────────────────────────
    // CONSULTANT FLOW
    // ─────────────────────────────
    private Boolean isConsultant = false;
    private Long actualClientCompanyId;

    // ─────────────────────────────
    // MODERN MULTI-LOCATION SUPPORT (preferred)
    // ─────────────────────────────
    @Valid
    private List<CompanyUnitRequestDto> units = new ArrayList<>();

    // ─────────────────────────────
    // LEGACY ADDRESSES (DEPRECATED)
    // Use ONLY when units is empty; service may auto-create a default unit from these.
    // ─────────────────────────────
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

    // ─────────────────────────────
    // AUDIT (optional)
    // ─────────────────────────────
    private Long createdById;
    private Long updatedById;
}
