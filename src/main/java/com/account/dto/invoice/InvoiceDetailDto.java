package com.account.dto.invoice;

import com.account.domain.InvoiceStatus;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class InvoiceDetailDto {

    // Basic info (same as summary + more)
    private Long id;
    private String publicUuid;
    private String invoiceNumber;
    private String unbilledNumber;
    private String estimateNumber;
    private String companyName;
    private String contactName;
    private LocalDate invoiceDate;
    private String currency;
    private InvoiceStatus status;
    private String irn;
    private String placeOfSupplyStateCode;
    private String buyerGstin;
    private String sellerGstin;

    // Financials
    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal grandTotal;

    // Audit
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Detailed line items
    private List<LineItemDto> lineItems;

    @Data
    @Getter
    @Setter
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