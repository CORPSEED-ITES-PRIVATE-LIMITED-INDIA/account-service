package com.account.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class EstimateCreationRequestDto {

    @NotNull(message = "Company ID is required")
    private Long companyId;

    private Long unitId; // Optional

    private Long contactId; // Optional

    @NotBlank(message = "Solution name is required")
    private String solutionName;

    @NotBlank(message = "Solution type is required")
    private String solutionType;

    private String solutionId;

    private LocalDate estimateDate;

    private LocalDate validUntil;

    private String customerNotes;

    private String internalRemarks;

    @NotNull(message = "Created by user ID is required ")
    private Long createdByUserId;

    @NotEmpty(message = "At least one line item is required")
    private List<EstimateLineItemDto> lineItems;

    @Getter
    @Setter
    public static class EstimateLineItemDto {

        private Long sourceItemId;

        @NotBlank
        private String itemName;

        private String description;

        private String hsnSacCode;

        @Min(1)
        private Integer quantity = 1;

        private String unit = "NOS";

        @NotNull
        @DecimalMin("0.0")
        private BigDecimal unitPriceExGst;

        @DecimalMin("0.0")
        private BigDecimal gstRate = BigDecimal.ZERO;

        private String categoryCode;

        private String feeType;
    }
}