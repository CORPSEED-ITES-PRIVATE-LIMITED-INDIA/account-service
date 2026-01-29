package com.account.dto.company.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

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

    // You can keep these as strings or use IDs – here strings for simplicity
    private String gstTypeEntity;
    private String gstBusinessType;
    private String gstTypePrice;

    private Long primaryContactId;    // optional
    private Long secondaryContactId;  // optional

    private LocalDate unitOpeningDate;
    private String status = "Active";

    private Boolean consultantPresent;
}