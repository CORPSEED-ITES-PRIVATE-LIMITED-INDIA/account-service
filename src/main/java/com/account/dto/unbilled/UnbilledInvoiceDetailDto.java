package com.account.dto.unbilled;

import com.account.domain.UnbilledStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UnbilledInvoiceDetailDto {

    private Long id;
    private String publicUuid;
    private String unbilledNumber;
    private String estimateNumber;
    private String companyName;
    private String contactName;
    private LocalDate invoiceDate;
    private String currency;
    private UnbilledStatus status;

    private String solutionName;
    private String solutionType;

    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal grandTotal;
    private BigDecimal receivedAmount;
    private BigDecimal outstandingAmount;

    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String approvalRemarks;

    private List<LineItemDto> lineItems;

    @Data
    public static class LineItemDto {
        private Long id;
        private Long sourceEstimateLineItemId;
        private String itemName;
        private String description;
        private String hsnSacCode;
        private Integer quantity;
        private String unit;
        private BigDecimal unitPriceExGst;
        private BigDecimal lineTotalExGst;
        private BigDecimal gstRate;
        private BigDecimal gstAmount;
        private BigDecimal lineTotalWithGst;
        private BigDecimal cgstAmount;
        private BigDecimal sgstAmount;
        private BigDecimal igstAmount;
        private Integer displayOrder;
        private String categoryCode;
        private String feeType;
    }
}