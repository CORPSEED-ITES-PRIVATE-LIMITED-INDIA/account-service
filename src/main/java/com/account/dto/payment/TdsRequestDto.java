package com.account.dto.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TdsRequestDto {

    @NotNull(message = "TDS percentage is required")
    @DecimalMin(
            value = "0.01",
            message = "TDS percentage must be greater than zero"
    )
    private BigDecimal tdsPercentage;

    /**
     * Current payment's service/taxable value excluding GST.
     *
     * Example:
     * Invoice taxable value = 50,000
     * Current taxable portion = 25,000
     * TDS at 10% = 2,500
     */
    @NotNull(message = "TDS taxable amount is required")
    @DecimalMin(
            value = "0.01",
            message = "TDS taxable amount must be greater than zero"
    )
    private BigDecimal taxableAmount;
}