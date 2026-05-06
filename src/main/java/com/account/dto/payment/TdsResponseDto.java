package com.account.dto.payment;

import com.account.domain.TdsStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TdsResponseDto {

    private Long id;
    private String publicUuid;

    private Long estimateId;
    private String estimateNumber;

    private Long unbilledInvoiceId;
    private String unbilledNumber;

    private BigDecimal tdsPercentage;
    private BigDecimal taxableAmount;
    private BigDecimal tdsAmount;

    private TdsStatus status;

    private Long createdById;
    private String createdByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}