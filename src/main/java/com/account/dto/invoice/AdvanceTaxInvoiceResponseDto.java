package com.account.dto.invoice;

import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import com.account.domain.invoice.InvoiceOrigin;
import com.account.domain.invoice.InvoicePaymentStatus;
import com.account.domain.status.InvoiceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class AdvanceTaxInvoiceResponseDto {

    // =====================================================
    // ADVANCE TAX INVOICE REQUEST
    // =====================================================

    private Long requestId;

    /**
     * AdvanceTaxInvoiceRequest UUID.
     */
    private String publicUuid;

    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;

    private AdvanceTaxInvoiceRequestStatus requestStatus;

    private String requestRemarks;
    private String reviewRemarks;

    private Long requestedByUserId;
    private String requestedByName;

    private Long reviewedByUserId;
    private String reviewedByName;

    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    // =====================================================
    // ESTIMATE
    // =====================================================

    private Long estimateId;
    private String estimateNumber;
    private BigDecimal estimateGrandTotal;

    private Long solutionId;
    private String solutionName;

    // =====================================================
    // COMPANY / UNIT / CONTACT
    // =====================================================

    private Long companyId;
    private String companyName;

    private Long unitId;
    private String unitName;
    /**
     * GST number registered against the selected Company Unit.
     */
    private String unitGstNo;
    /**
     * Company Unit address details.
     */
    private String unitAddressLine1;
    private String unitAddressLine2;
    private String unitCity;
    private String unitState;
    private String unitCountry;
    private String unitPinCode;


    private Long contactId;
    private String contactName;

    // =====================================================
    // GENERATED INVOICE
    // =====================================================

    private Boolean invoiceGenerated;

    private Long invoiceId;
    private String invoicePublicUuid;
    private String invoiceNumber;

    /**
     * Null for the standard Advance Tax Invoice flow.
     */
    private String unbilledNumber;

    private InvoiceOrigin invoiceOrigin;

    private LocalDate invoiceDate;
    private String currency;

    private InvoiceStatus invoiceStatus;

    private String placeOfSupplyStateCode;

    private String buyerGstin;
    private String sellerGstin;

    private Boolean cancelled;

    // =====================================================
    // GST
    // =====================================================

    private String gstRegistrationType;
    private Boolean gstApplicable;
    private Boolean zeroRatedSupply;

    // =====================================================
    // INVOICE FINANCIALS
    // =====================================================

    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;

    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;

    private BigDecimal invoiceGrandTotal;

    // =====================================================
    // PAYMENT STATUS
    // =====================================================

    /**
     * UNPAID when an Advance Tax Invoice is first generated.
     */
    private InvoicePaymentStatus invoicePaymentStatus;

    private BigDecimal receivedAmount;
    private BigDecimal pendingReceivedAmount;

    /**
     * Outstanding minus payment amount currently pending approval.
     */
    private BigDecimal availableOutstandingAmount;

    private BigDecimal outstandingAmount;

    // =====================================================
    // E-INVOICE
    // =====================================================

    private String irn;
    private String eInvoiceAckNo;
    private LocalDateTime eInvoiceAckDate;

    private String eInvoiceAttachmentUrl;

    private LocalDateTime eInvoiceConfirmedAt;
    private Long eInvoiceConfirmedByUserId;
    private String eInvoiceConfirmedByName;

    private String eInvoiceRemarks;

    // =====================================================
    // ORGANIZATION / SELLER SNAPSHOT
    // =====================================================

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

    // =====================================================
    // ORGANIZATION BANK DETAILS
    // =====================================================

    private Boolean organizationBankAccountPresent;

    private String organizationAccountHolderName;
    private String organizationAccountNumber;

    private String organizationIfscCode;
    private String organizationSwiftCode;

    private String organizationBankName;
    private String organizationBankBranch;

    private String organizationUpiId;
    private String organizationPaymentPageLink;

    // =====================================================
    // INVOICE AUDIT
    // =====================================================

    private Long invoiceCreatedByUserId;
    private String invoiceCreatedByName;

    private LocalDateTime invoiceCreatedAt;
    private LocalDateTime invoiceUpdatedAt;

    // =====================================================
    // INVOICE LINE ITEMS
    // =====================================================

    /**
     * PENDING request:
     * mapped from EstimateLineItem.
     *
     * APPROVED/generated request:
     * mapped from InvoiceLineItem.
     *
     * Never null.
     */
    @Builder.Default
    private List<InvoiceDetailDto.LineItemDto> lineItems =
            Collections.emptyList();

    private String message;
}
