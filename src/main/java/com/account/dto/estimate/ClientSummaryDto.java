package com.account.dto.estimate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientSummaryDto {

    private Long id;
    private String title;
    private String name;
    private String email;
    private String contactNo;
    private String whatsappNo;
    private String clientDesignation;
    private String designation;
}