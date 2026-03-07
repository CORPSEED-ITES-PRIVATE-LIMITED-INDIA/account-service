package com.account.dto.company;


import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
@Getter
@Setter
public class FullContactCreationDto {

    @NotNull(message = "contactId is required (manual ID)")
    private Long id;

    private String title;
    @NotBlank(message = "Name is required")
    private String name;

    private String emails;
    private String contactNo;
    private String whatsappNo;

    private String clientDesignation;
    private String designation;

    // Flags to indicate role
    private boolean isPrimaryForCompany;
    private boolean isSecondaryForCompany;
    private boolean isPrimaryForUnit;
    private boolean isSecondaryForUnit;
}