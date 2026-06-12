package com.account.dto.invoice;

import com.account.domain.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSummaryDto {

    private Long id;
    private String publicUuid;
    private String invoiceNumber;
    private String unbilledNumber;
    private String estimateNumber;

    private Long paymentTypeId;
    private String paymentTypeCode;

    private Long estimateId;
    private Long solutionId;
    private String solutionName;
    private String solutionType;

    private String companyName;
    private String contactName;

    private LocalDate invoiceDate;
    private BigDecimal grandTotal;
    private BigDecimal totalGstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;

    private String irn;
    private InvoiceStatus status;

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

    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    private Long searchCount;
}