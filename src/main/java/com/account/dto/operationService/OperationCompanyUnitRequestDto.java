package com.account.dto.operationService;

import jakarta.persistence.Column;
import lombok.Data;

import java.util.Date;


@Data
public class OperationCompanyUnitRequestDto {


    private Long   unitId;
    private String unitName;                // Required
    private String address;                 // Required
    private String city;
    private String state;
    private String country = "India";
    private String pinCode;
    private String gstNo;
    private String gstType;
    private String gstBusinessType;
    private Date unitOpeningDate;
    private String status;
}
