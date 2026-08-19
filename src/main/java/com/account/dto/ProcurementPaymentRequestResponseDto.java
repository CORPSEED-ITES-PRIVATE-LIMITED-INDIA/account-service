package com.account.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
@Builder
public class ProcurementPaymentRequestResponseDto {
    private Long id;
    private Long procurementOrderId;
    private String poNumber;
    private Long projectId;
    private String projectName;
    private String projectNo;
    private Long vendorId;
    private String vendorName;
    private BigDecimal invoiceAmount;
    private BigDecimal payableAmount;
    private BigDecimal bankPaymentAmount;
    private BigDecimal settlementAmount;
    private LocalDate paymentDate;
    private String calculationVersion;
    private Date submissionDate;
    private String completionRemarks;
    private List<String> proofAttachmentUrls;
    private String status; // use String here instead of Operations' enum — Account doesn't have that enum class
    private Date approvedDate;
    private Date paymentReleasedDate;
    private Long createdBy;
    private Long approvedBy;
    private Long paymentReleasedBy;
    private Date createdDate;
    private Date updatedDate;
    private Boolean tdsActive;
    private BigDecimal tdsPercentage;
    private Boolean gstActive;
    private String gstStateCode;
    private BigDecimal gstPercentage;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalGstAmount;
    private BigDecimal amount;
    private String paymentMode;
    private Long bankLedgerId;
    private Long ledgerId;
    private String ledgerType;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private String transactionReference;
    private String paymentProof;
    private BigDecimal tdsAmount;
    private String gstType;
    private String paymentTerms;
    private Integer paymentTermDays;
    private LocalDate paymentRequestDate;
    private LocalDate paymentDueDate;
    private Long remainingDays;
    private Long overdueDays;
    private Boolean overdue;
    private String paymentDueStatus;

    // Organization fields — populated locally in Account, not from Operations
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
    private Boolean organizationBankAccountPresent;
    private String organizationAccountHolderName;
    private String organizationAccountNumber;
    private String organizationIfscCode;
    private String organizationSwiftCode;
    private String organizationBankName;
    private String organizationBankBranch;
    private String organizationUpiId;
    private String organizationPaymentPageLink;
}