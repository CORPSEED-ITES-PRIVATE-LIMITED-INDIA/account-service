package com.account.dto.estimate;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Estimate details.
 * Returned to frontend/salesperson/customer after creation or fetch.
 * Contains all necessary fields including public sharing UUID and GST breakup.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstimateResponseDto {

    // Internal ID - used only internally (not exposed in public views)
    private Long id;

    // Secure public identifier for sharing links/PDF/email/WhatsApp
    private String publicUuid;  // UUID v4 - safe for public exposure

    private Long leadId;

    // Human-readable estimate number
    private String estimateNumber;

    private LocalDate estimateDate;
    private LocalDate validUntil;

    private String solutionName;           // e.g. "FSSAI State License - Uttar Pradesh"
    private String solutionType;           // SERVICE, PRODUCT, PLANT_SETUP
    private String status;                 // DRAFT, SENT_TO_CLIENT, APPROVED, etc.

    private String currency;               // INR

    // Financial breakdown
    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;

    // GST breakup (very important for Indian compliance & customer visibility)
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;

    private BigDecimal grandTotal;

    private String customerNotes;
    private String internalRemarks;

    // Revision tracking
    private Integer version;
    private String revisionReason;

    // Audit fields
    private LocalDateTime createdAt;
    private Long createdById;              // ID of user who created it
    private String createdByName;          // Optional - full name for display

    private CompanySummaryDto company;
    private CompanyUnitSummaryDto unit;


    // Line items breakdown
    private List<EstimateLineItemResponseDto> lineItems;

    /**
     * Inner DTO for each estimate line item in response
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimateLineItemResponseDto {

        private Long id;
        private Long sourceItemId;          // Original FeeComponent/ProductFeeRule ID

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