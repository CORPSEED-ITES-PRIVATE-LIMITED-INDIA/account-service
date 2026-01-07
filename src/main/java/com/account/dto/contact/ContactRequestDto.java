package com.account.dto.contact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequestDto {

    @NotBlank(message = "Name is required")
    private String name;

    private String emails;

    private String contactNo;

    private String whatsappNo;

    @NotNull(message = "Company ID is required")
    private Long companyId;
}