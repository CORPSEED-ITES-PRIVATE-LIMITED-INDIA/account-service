package com.account.dto.company;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
public class CompanyUnitResponseDto {

    private Long id;
    private String unitName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private String gstNo;
    private String status;
    private Date unitOpeningDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}