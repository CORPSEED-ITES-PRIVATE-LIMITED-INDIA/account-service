package com.account.dto.invoice;

import com.account.domain.status.InvoiceStatus;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class InvoiceDetailDto {

    // Basic invoice information
    private Long id;
    private String publicUuid;
    private String invoiceNumber;
    private String unbilledNumber;
    private String estimateNumber;

    private Long companyId;
    private String companyName;

    private Long companyUnitId;
    private String companyUnitName;
    private String companyUnitAddressLine1;
    private String companyUnitAddressLine2;
    private String companyUnitCity;
    private String companyUnitState;
    private String companyUnitCountry;
    private String companyUnitPinCode;
    private String companyUnitGstNo;

    private String contactName;

    private LocalDate invoiceDate;
    private String currency;
    private InvoiceStatus status;
    private String irn;
    private String placeOfSupplyStateCode;
    private String buyerGstin;
    private String sellerGstin;

    private Long solutionId;
    private String solutionName;

    // Financials
    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal grandTotal;

    // Seller/organization details
    private String organizationName;
    private String organizationAddressLine1;
    private String organizationAddressLine2;
    private String organizationCity;
    private String organizationState;
    private String organizationCountry;
    private String organizationPinCode;
    private String organizationGstNo;
    private String organizationPanNo;
    private String organizationCinNumber;
    private String organizationEmail;
    private String organizationPhone;
    private String organizationWebsite;
    private String organizationLogoUrl;

    private String gstRegistrationType;
    private Boolean gstApplicable;
    private Boolean zeroRatedSupply;

    // Audit
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
        private Boolean igstFlag;
    }
}