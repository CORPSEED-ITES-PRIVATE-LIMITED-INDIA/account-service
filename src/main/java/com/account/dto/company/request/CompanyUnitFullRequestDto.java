package com.account.dto.company.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class CompanyUnitFullRequestDto {

    // If present → update existing unit (must match lead service unit ID)
    private Long id;               // lead service unit ID

    @NotBlank(message = "Unit name is required")
    private String unitName;

    @NotBlank(message = "Address line 1 is required")
    private String addressLine1;

    private String addressLine2;
    private String city;
    private String state;
    private String country = "India";
    private String pinCode;

    private String gstNo;
    private String gstRegistrationType;

    private Long primaryContactId;    // optional
    private Long secondaryContactId;  // optional

    private Date unitOpeningDate;
    private String status = "Active";

    private Boolean consultantPresent;
}