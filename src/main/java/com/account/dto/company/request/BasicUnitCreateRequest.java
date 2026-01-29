package com.account.dto.company.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BasicUnitCreateRequest {

    // If provided → this is UPDATE of existing unit
    // If null / not provided → this is CREATE new unit
    private Long companyUnitId;

    @Size(max = 150, message = "Address cannot exceed 150 characters")
    private String address;

    @Size(max = 100, message = "City name too long")
    private String city;

    @Size(max = 100, message = "State name too long")
    private String state;

    private String country;

    @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Invalid PIN code format")
    private String pinCode;

    @NotNull(message = "createdById is required for new units")
    private Long createdById;

    // Usually sent by frontend / security context
    private Long updatedById;

    @Size(min = 2, max = 120, message = "Unit name must be 2-120 characters")
    private String unitName;

    @Pattern(
            regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}[Z]{1}[0-9A-Z]{1}$",
            message = "Invalid GSTIN format. Correct example: 27AAAAA0000A1Z5"
    )
    private String gstNo;

    @Pattern(
            regexp = "^[A-Z]{5}[0-9]{4}[A-Z]{1}$",
            message = "Invalid PAN format. Correct example: ABCDE1234F"
    )
    private String panNo;
}