package com.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter
@Setter
public class BasicCompanyRequestDto {

    @NotBlank(message = "Company name is required")
    @Size(max = 255, message = "Company name too long")
    private String name;

    private String address;
    private String city;
    private String state;
    private String Country;
    private String pinCode;

    @NotNull(message = "createdById is required")  // if mandatory
    private Long createdById;

    private Long updatedById;
    private String unitName;

    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
            message = "Invalid GST format. Expected: 27AAAAA0000A1Z5")
    private String gstNo;

    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$",
            message = "Invalid PAN format. Expected: ABCDE1234F")
    private String panNo;

    private Long leadId;
}