package com.account.domain.contact;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ContactCreationDto {

    @NotNull(message = "Contact ID is required")
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String title;
    private String emails;               // comma-separated or JSON if multiple
    private String contactNo;
    private String whatsappNo;
    private String designation;

    private Long clientDesignationId;    // optional - reference to ClientDesignation entity

    // Association - at least one must be provided
    private Long companyId;              // required if no unit
    private Long companyUnitId;          // required if no company (or both allowed)

    // Flags - which role this contact plays
    private boolean makePrimaryForCompany = false;
    private boolean makeSecondaryForCompany = false;
    private boolean makePrimaryForUnit = false;
    private boolean makeSecondaryForUnit = false;
}