package com.account.dto;


import com.account.dto.company.migrate.ContactSyncDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class CompanyUnitMigrationDto {

    @NotNull(message = "unitId is mandatory")
    private Long id;                    // decisive ID for this unit

    @NotBlank(message = "Unit name is required")
    private String unitName;

    // ── Address ──────────────────────────────────────────────────
    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    private String country = "India";

    @NotBlank(message = "PIN code is required")
    private String pinCode;

    // ── GST related ──────────────────────────────────────────────
    private String gstNo;
    private String gstType;
    private String gstDocuments;           // JSON / comma separated / base64
    private String gstTypeEntity;
    private String gstBusinessType;
    private String gstTypePrice;

    private Date unitOpeningDate;

    private String status = "Active";

    private Boolean consultantPresent = false;

    // ── Accounts / Onboarding ────────────────────────────────────
    private Boolean accountsApproved = false;
    private String accountsRemark;
    private String onboardingStatus;

    @Valid
    private List<ContactSyncDto> contacts = new ArrayList<>();
}
