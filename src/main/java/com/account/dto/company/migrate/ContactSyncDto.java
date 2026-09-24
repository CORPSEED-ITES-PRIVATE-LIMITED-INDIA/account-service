package com.account.dto.company.migrate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactSyncDto {

    @NotNull(message = "contactId is mandatory")
    private Long id;

    private String title;

    @NotBlank(message = "Contact name is required")
    private String name;

    private String emails;                // comma separated or JSON array string

    private String contactNo;
    private String whatsappNo;

    private String clientDesignation;
    private String designation;

    // ── Role flags ───────────────────────────────────────────────
    // (in most real cases at least one should be true)
    private boolean primaryForCompany = false;
    private boolean secondaryForCompany = false;
    private boolean primaryForUnit = false;
    private boolean secondaryForUnit = false;
}