package com.account.dto.invoice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvanceTaxInvoiceRejectionRequestDto {

    @NotNull(message = "rejectedByUserId is required")
    @Positive(message = "rejectedByUserId must be greater than zero")
    private Long rejectedByUserId;

    @NotBlank(message = "Rejection reason is required")
    @Size(
            max = 1000,
            message = "Rejection reason cannot exceed 1000 characters"
    )
    private String rejectionReason;
}