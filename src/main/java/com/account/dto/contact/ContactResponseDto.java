package com.account.dto.contact;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponseDto {

    private Long id;
    private String title;
    private String name;
    private String emails;
    private String contactNo;
    private String whatsappNo;
    private String designation;

    // Optional: include IDs only (no full objects)
    private Long companyId;
    private Long companyUnitId;

    // Flags
    private boolean isPrimaryForCompany;
    private boolean isSecondaryForCompany;
    private boolean isPrimaryForUnit;
    private boolean isSecondaryForUnit;

    private boolean isDeleted;
}