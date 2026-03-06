package com.account.dto.company;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class FullUnitCreationDto {

    @NotNull(message = "unitId is required (manual ID)")
    private Long id;

    @NotBlank(message = "Unit name is required")
    private String unitName;

    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    private String country = "India";

    @NotBlank(message = "Pin code is required")
    private String pinCode;

    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
            message = "Invalid GSTIN format")
    private String gstNo;

    private String gstType;
    private String gstDocuments;
    private String gstTypeEntity;
    private String gstBusinessType;
    private String gstTypePrice;

    private LocalDate unitOpeningDate;

    private String status = "Active";

    private boolean consultantPresent;

    // Contacts specific to this unit
    @Valid
    private List<FullContactCreationDto> unitContacts;
}