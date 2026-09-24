package com.account.dto.payment;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentFeeRequestDto {

    @NotNull(message = "Government fee total amount is required")
    @Positive(message = "Government fee total amount must be positive")
    private BigDecimal totalAmount;

    @NotNull(message = "Government fee received amount is required")
    @Positive(message = "Government fee received amount must be positive")
    private BigDecimal receivedAmount;

    private LocalDate paymentDate;

    @Size(max = 50, message = "Fee reference number must not exceed 50 characters")
    private String feeReferenceNumber;

    @Size(max = 100, message = "Department name must not exceed 100 characters")
    private String departmentName;

    @Size(max = 100, message = "Fee type must not exceed 100 characters")
    private String feeType;

    private String remarks;
}