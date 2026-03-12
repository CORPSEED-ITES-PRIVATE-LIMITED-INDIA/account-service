package com.account.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CompanyMigrationRequestDto {

    @NotNull(message = "companyId is mandatory")
    private Long companyId;           // decisive ID — must always be sent

    private String uuid;              // optional — will generate if missing

    @NotBlank(message = "Company name is required")
    private String name;

    private String panNo;

    private LocalDate establishDate;

    private String industry;
    private String subIndustry;
    private String subsubIndustry;

    private String status;

    private Boolean isConsultant = false;

    // ── Agreements ───────────────────────────────────────────────
    private String paymentTerm;
    private Boolean agreementPresent = false;
    private String agreement;
    private Boolean ndaPresent = false;
    private String nda;
    private String revenue;

    // ── Onboarding / Accounts ────────────────────────────────────
    private String onboardingStatus;     // string name of enum or null
    private Boolean accountsApproved = false;
    private String accountsRemark;

    // ── Audit fields (usually managed by source system or service) ──
    private Long createdById;
    private Long updatedById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<CompanyUnitMigrationDto> units;

}
