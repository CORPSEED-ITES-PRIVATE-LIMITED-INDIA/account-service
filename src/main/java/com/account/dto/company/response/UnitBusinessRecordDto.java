package com.account.dto.company.response;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class UnitBusinessRecordDto {

    private Long estimateId;
    private String estimateNumber;
    private String estimatePublicUuid;
    private String solutionName;
    private Long solutionId;
    private String solutionType;
    private String estimateStatus;
    private LocalDate estimateDate;
    private BigDecimal estimateGrandTotal;

    private Long unbilledId;
    private String unbilledNumber;
    private String unbilledPublicUuid;
    private String unbilledStatus;
    private BigDecimal unbilledTotalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal outstandingAmount;
    private Boolean governmentFeeActive;

    private Long operationProjectId;
    private String operationProjectName;
    private String operationProjectStatus;
    private String projectUnbilledNumber;
    private Double projectTotalAmount;
    private Double projectDueAmount;
}