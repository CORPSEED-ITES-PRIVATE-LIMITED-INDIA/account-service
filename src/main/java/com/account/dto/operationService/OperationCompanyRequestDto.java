package com.account.dto.operationService;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Data
public class  OperationCompanyRequestDto {

    // Company basic info
    private String name;
    private String panNo;
    private LocalDate establishDate;
    private String industry;
    private String industries;
    private String subIndustry;
    private String subSubIndustry;

    // Who is creating this company
    private Long createdBy;                 // Required - user ID

    private String rating;


    // Optional: List of units to create along with company
    private List<OperationCompanyUnitRequestDto> units = new ArrayList<>();

    // Optional: List of contacts (company-level or unit-level)
    private List<OperationContactRequestDto> contacts = new ArrayList<>();
}
