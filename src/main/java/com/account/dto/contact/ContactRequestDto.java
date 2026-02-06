package com.account.dto.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestDto {

    // Required when creating → client must provide ID
    @NotNull(message = "ID is required when creating a contact")
    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    private String emails;

    private String contactNo;

    private String whatsappNo;

    @NotNull(message = "Company ID is required")
    private Long companyId;
}