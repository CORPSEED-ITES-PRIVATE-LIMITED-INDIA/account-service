package com.account.dto.contact;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactResponseDto {

    private Long id;
    private String name;
    private String emails;
    private String contactNo;
    private String whatsappNo;
    private Long companyId;
}