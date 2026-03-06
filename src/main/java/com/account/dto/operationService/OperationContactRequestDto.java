package com.account.dto.operationService;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;


@Data
public class OperationContactRequestDto {


    private Long contactId;

    @NotBlank(message = "Contact name is required")
    @Size(max = 150, message = "Name cannot exceed 150 characters")
    private String name;

    private String title;

    @NotBlank(message = "Designation is required")
    @Size(max = 100, message = "Designation cannot exceed 100 characters")
    private String designation;
    private Long clientDesignationId;


    @Email(message = "Invalid email format")
    @Size(max = 120, message = "Email cannot exceed 120 characters")
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid mobile number format")
    @Size(max = 15, message = "Mobile number cannot exceed 15 characters")
    private String contactNo;

    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid mobile number format")
    @Size(max = 15, message = "Mobile number cannot exceed 15 characters")
    private String whatsappNo;


    // For company-level contact (when attaching directly to company)
    private Long companyId;
    // For unit-level contact (when attaching to a specific unit)
    private Long unitId;             // required if this contact belongs to a unit

    // Priority / type flags
    private Boolean isPrimary;       // default false — whether this is the main contact
    private Boolean isSecondary;     // default false — secondary/alternate contact

    private boolean makePrimaryForCompany = false;
    private boolean makeSecondaryForCompany = false;
    private boolean makePrimaryForUnit = false;
    private boolean makeSecondaryForUnit = false;

    private Long createdBy;
    private Long updatedBy;


}
