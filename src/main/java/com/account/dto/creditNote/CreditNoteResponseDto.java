package com.account.dto.creditNote;

import com.account.domain.creditNote.CreditNoteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditNoteResponseDto {

    private Long id;

    private String creditNoteNumber;

    private Long proposalId;

    private Long unbilledId;

    private String unbilledNumber;

    private Long estimateId;

    private String estimateNumber;

    private String attachment;

    private Long companyId;

    private String companyName;

    private Long contactId;

    private String contactName;

    private BigDecimal totalAmount;

    private BigDecimal receivedAmount;

    private BigDecimal currentReceivedAmount;

    private BigDecimal outstandingAmount;

    private BigDecimal refundAmount;

    private BigDecimal creditAmount;

    private BigDecimal utilizedCreditAmount;

    private BigDecimal remainingCreditAmount;

    private CreditNoteStatus status;

    private String reason;

    private Long createdById;

    private LocalDateTime createdAt;

    // Account team review fields
    private Long accountApprovedById;

    private LocalDateTime accountApprovedAt;

    private String accountApprovalRemarks;

    // Final admin approval fields
    private Long approvedById;

    private LocalDateTime approvedAt;

    private String approvalRemarks;

    private String rejectionReason;

    private Long rejectedById;

    private LocalDateTime rejectedAt;

    private LocalDateTime updatedAt;

    private List<CreditNoteInvoiceDetailResponseDto> invoices;

    private String gstPortalAttachment;

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
    // ORGANIZATION BANK SNAPSHOT
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


}