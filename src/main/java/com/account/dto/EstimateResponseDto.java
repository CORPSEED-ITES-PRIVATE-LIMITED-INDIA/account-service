package com.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimateResponseDto {

    private Long id;
    private String estimateNumber;
    private LocalDate estimateDate;
    private LocalDate validUntil;
    private String solutionName;
    private String solutionType;
    private String status;
    private String currency;
    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;
    private BigDecimal grandTotal;
    private String customerNotes;
    private String internalRemarks;
    private Integer version;
    private String revisionReason;
    private LocalDateTime createdAt;
    private Long createdById;

    private List<EstimateLineItemResponseDto> lineItems;

    @Getter
    @Setter
    public static class EstimateLineItemResponseDto {
        private Long id;
        private String itemName;
        private String description;
        private String hsnSacCode;
        private Integer quantity;
        private String unit;
        private BigDecimal unitPriceExGst;
        private BigDecimal gstRate;
        private BigDecimal lineTotalExGst;
        private BigDecimal gstAmount;
        private Integer displayOrder;
        private String categoryCode;
        private String feeType;
    }
}