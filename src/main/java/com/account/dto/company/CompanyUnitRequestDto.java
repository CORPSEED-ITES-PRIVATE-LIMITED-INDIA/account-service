package com.account.dto.company;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyUnitRequestDto {

    private Long leadUnitId; // To maintain same ID from lead-service

    private String unitName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private String gstNo;

    private Long gstTypeId;
    private Long gstBusinessTypeId;
    private Long gstTypePriceId;

    private Long primaryContactId;
    private Long secondaryContactId;

    private java.util.Date unitOpeningDate;
    private Boolean consultantPresent;
}