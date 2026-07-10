package com.account.dto.operationService;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;


@Data
public class OperationProjectResponseDto {

    private Long id;
    private String name;
    private String projectNo;
    private String unbilledNumber;
    private String estimateNumber;

    // Sales Person
    private Long salesPersonId;
    private String salesPersonName;          // NEW

    private Long productId;
    private Long companyId;
    private String companyName;              // NEW

    private Long contactId;
    private String contactName;              // NEW

    private Long leadId;
    private LocalDate date;
    private Double totalAmount;
    private Double dueAmount;
    private String paymentStatus;
    private Long paymentTypeId;
    private Long approvedById;

    private Date createdDate;
    private Date updatedDate;

    private boolean isDeleted;
    private boolean isActive;

    private Long statusId;
    private String statusName;

    private Long unitId;
    private String unitName;

    private Integer milestoneCompletionPercentage;

    private Boolean poBillingEligible;
}
