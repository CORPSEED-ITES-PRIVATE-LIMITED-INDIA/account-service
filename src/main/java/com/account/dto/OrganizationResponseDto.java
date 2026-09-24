package com.account.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class OrganizationResponseDto {

    private Long id;

    private String name;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String pinCode;

    private String gstNo;

    private String panNo;

    private String cinNumber;

    private LocalDate establishedDate;

    private String ownerName;

    private boolean bankAccountPresent;

    private String accountHolderName;

    private String accountNo;

    private String ifscCode;

    private String swiftCode;

    private String bankName;

    private String branch;

    private String upiId;

    private String website;

    private String paymentPageLink;

    private String estimateConditions;

    private String logoUrl;

    private String email;

    private String phone;

    private boolean active;

    private Long createdById;

    private Long updatedById;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}