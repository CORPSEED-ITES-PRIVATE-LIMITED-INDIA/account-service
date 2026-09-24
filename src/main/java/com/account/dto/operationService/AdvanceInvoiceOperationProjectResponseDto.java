package com.account.dto.operationService;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvanceInvoiceOperationProjectResponseDto {
    private Long id;
    private String projectNo;
    private Long estimateId;
    private Long leadId;
    private boolean existing;
    private String message;
}
