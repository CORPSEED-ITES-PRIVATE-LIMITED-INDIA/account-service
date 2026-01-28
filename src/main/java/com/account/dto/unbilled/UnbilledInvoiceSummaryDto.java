package com.account.dto.unbilled;

import com.account.domain.UnbilledStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class UnbilledInvoiceSummaryDto {

    private Long id;
    private String unbilledNumber;
    private String estimateNumber;          // From linked estimate
    private Long estimateId;
    private String companyName;             // Summary
    private String contactName;             // Summary
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal outstandingAmount;
    private UnbilledStatus status;
    private LocalDateTime createdAt;
    private String createdByName;           // Salesperson
    private LocalDateTime approvedAt;       // Null if not approved
    private String approvedByName;          // Null if not approved
}