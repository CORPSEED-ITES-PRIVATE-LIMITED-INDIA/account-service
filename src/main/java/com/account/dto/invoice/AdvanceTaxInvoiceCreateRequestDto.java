package com.account.dto.invoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AdvanceTaxInvoiceCreateRequestDto {

    @NotNull(message = "estimateId is required")
    private Long estimateId;

    /*
     * Normal Advance Tax Invoice:
     * requestedAmount must be provided.
     *
     * Completed zero-value PURCHASE_ORDER conversion:
     * requestedAmount may be omitted or sent as null.
     * The backend automatically uses the complete remaining
     * invoiceable amount.
     */
    @DecimalMin(
            value = "0.01",
            inclusive = true,
            message = "requestedAmount must be greater than zero"
    )
    private BigDecimal requestedAmount;

    @NotNull(message = "requestedByUserId is required")
    private Long requestedByUserId;

    @Size(
            max = 5000,
            message = "requestRemarks cannot exceed 5000 characters"
    )
    private String requestRemarks;
}