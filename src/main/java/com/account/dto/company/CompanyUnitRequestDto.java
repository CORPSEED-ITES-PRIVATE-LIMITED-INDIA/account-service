package com.account.dto.company;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class CompanyUnitRequestDto {

    /**
     * Lead/external unit identifier.
     * NOTE: If CompanyUnit.id is @GeneratedValue, do NOT store this into id.
     * Store it in CompanyUnit.leadUnitId (separate column).
     */
    private Long leadUnitId;

    // ─────────────────────────────
    // UNIT INFO
    // ─────────────────────────────
    @NotBlank(message = "Unit name is required")
    @Size(max = 255, message = "Unit name cannot exceed 255 characters")
    private String unitName;

    // Address (recommended required in unit-based model)
    @NotBlank(message = "addressLine1 is required")
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = "city is required")
    private String city;

    @NotBlank(message = "state is required")
    private String state;

    @NotBlank(message = "country is required")
    private String country;

    @NotBlank(message = "pinCode is required")
    private String pinCode;

    // ─────────────────────────────
    // GST (unit-level)
    // Validate only if present; allows null/blank
    // ─────────────────────────────
    @Pattern(
            regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$",
            message = "Invalid GST format"
    )
    private String gstNo;
    private String gstTypeEntity;
    private String gstBusinessType;
    private String gstTypePrice;

    private Long primaryContactId;
    private Long secondaryContactId;

    private Date unitOpeningDate;
    private Boolean consultantPresent;
}
