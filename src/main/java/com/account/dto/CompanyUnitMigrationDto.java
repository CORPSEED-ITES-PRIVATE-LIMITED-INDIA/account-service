package com.account.dto;


import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyUnitMigrationDto {

    // Identity
    private Long unitId;
    private Long leadUnitId; // optional but useful for traceability

    // Core
    private String unitName;

    // Address
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pinCode;

    // GST
    private String gstNo;
    private String gstType;
    private String gstDocuments;
    private String gstTypeEntity;
    private String gstBusinessType;
    private String gstTypePrice;

    // Dates / status
    private LocalDate unitOpeningDate;
    private String status;
    private boolean consultantPresent;

    // Audit
    private Long createdById;
    private Long updatedById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Accounts
    private boolean accountsApproved;
    private String accountsRemark;
    private String onboardingStatus;
}
