package com.account.dto;


import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class CompanyMigrationRequestDto {

    // ===== Identity =====
    private Long companyId;          // Lead company ID (primary key)
    private String uuid;

    // ===== Basic Info =====
    private String name;
    private String panNo;

    // ===== Dates =====
    private LocalDate establishDate;

    // ===== Address =====
    private String address;
    private String city;
    private String state;
    private String country;
    private String primaryPinCode;

    // ===== Industry Hierarchy =====
    private String industry;
    private String industries;
    private String subIndustry;
    private String subSubIndustry;

    // ===== Business / Status =====
    private String status;
    private Boolean isConsultant;
    private String rating;
    private String companyAge;

    // ===== Agreements =====
    private String paymentTerm;
    private boolean aggrementPresent;
    private String aggrement;
    private boolean ndaPresent;
    private String nda;
    private String revenue;

    // ===== Audit =====
    private boolean accountsApproved;
    private String accountsRemark;
    private String onboardingStatus;

    private Long createdById;
    private Long updatedById;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CompanyUnitMigrationDto> units;

}
