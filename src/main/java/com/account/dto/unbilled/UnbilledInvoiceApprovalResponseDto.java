package com.account.dto.unbilled;// Response DTO


import com.account.domain.UnbilledStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UnbilledInvoiceApprovalResponseDto {

    private String unbilledNumber;
    private UnbilledStatus status;
    private String invoiceNumber;
    private Long invoiceId;
    private String approvedByName;           // or full name / email
    private LocalDateTime approvedAt;
    private String approvalRemarks;
    private String message;
}