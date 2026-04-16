package com.account.dto.payment;

import com.account.domain.GovernmentFeeStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentFeeResponseDto {

    private Long id;
    private String publicUuid;

    private Long estimateId;
    private String estimateNumber;

    private Long unbilledInvoiceId;
    private String unbilledNumber;

    private Long companyId;
    private String companyName;

    private Long unitId;
    private String unitName;

    private Long contactId;
    private String contactName;

    private String feeReferenceNumber;
    private String departmentName;
    private String feeType;

    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal outstandingAmount;

    private LocalDate paymentDate;
    private LocalDate dueDate;

    private GovernmentFeeStatus status;
    private String remarks;

    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}