package com.account.dto.invoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AdvanceTaxInvoiceApprovalRequestDto {

    @NotNull(message = "approverUserId is required")
    private Long approverUserId;

    @NotNull(message = "approvedAmount is required")
    @DecimalMin(
            value = "0.01",
            inclusive = true,
            message = "approvedAmount must be greater than zero"
    )
    private BigDecimal approvedAmount;

    @Size(
            max = 5000,
            message = "reviewRemarks cannot exceed 5000 characters"
    )
    private String reviewRemarks;
}
