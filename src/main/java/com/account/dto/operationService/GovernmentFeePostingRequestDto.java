package com.account.dto.operationService;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentFeePostingRequestDto {

    @NotNull(message = "Operation expense ID is required")
    private Long operationExpenseId;

    private Long projectId;

    private String projectNo;

    private String projectName;

    private String expenseCategory;

    @NotNull(message = "Approved amount is required")
    @DecimalMin(
            value = "0.01",
            message = "Approved amount must be greater than zero"
    )
    private BigDecimal approvedAmount;

    private String currencyCode;

    private LocalDate expenseDate;

    @NotNull(message = "Paid by is required")
    private GovernmentFeePaidBy paidBy;

    private Long approvedByUserId;

    private String approvedByUserName;

    private String narration;
}