package com.account.dto.operationService;

import lombok.Data;

import java.util.Date;
import java.util.List;


@Data
public class OperationCompanyResponseDto {

    private Long id;
    private String name;
    private String panNo;
    private Date establishDate;

    private String industry;
    // private String industries;   // ← removed if duplicate/not needed

    private String subIndustry;
    private String subSubIndustry;

    private Date createdDate;
    private Date updatedDate;
    private boolean deleted;

    private int unitCount;
    private int contactCount;
}
