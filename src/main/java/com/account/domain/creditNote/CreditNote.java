package com.account.domain.creditNote;

import com.account.domain.company.Company;
import com.account.domain.Contact;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.domain.User;
import com.account.domain.estimate.Estimate;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "credit_note",
        indexes = {
                @Index(name = "idx_credit_note_number_unique", columnList = "credit_note_number", unique = true),
                @Index(name = "idx_credit_note_unbilled_id", columnList = "unbilled_id"),
                @Index(name = "idx_credit_note_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credit_note_number", nullable = false, unique = true, length = 40)
    private String creditNoteNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unbilled_id", nullable = false)
    private UnbilledInvoice unbilledInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id")
    private Estimate estimate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(name = "unbilled_number", length = 40)
    private String unbilledNumber;

    @Column(name = "estimate_number", length = 40)
    private String estimateNumber;

    @Column(name = "proposal_number",length = 40)
    private String proposalNumber;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "attachment")
    private String attachment;


    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "received_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal receivedAmount;

    @Column(name = "current_received_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal currentReceivedAmount;

    @Column(name = "outstanding_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal outstandingAmount;

    @Column(name = "credit_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal creditAmount;

    @Column(name = "refund_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal refundAmount;


    private BigDecimal utilizedCreditAmount;  // Amount already used in future invoices
    private BigDecimal remainingCreditAmount; // Balance still available

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditNoteStatus status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String approvalRemarks;

    @Column(name = "organization_name", length = 255)
    private String organizationName;

    @Column(name = "organization_address_line1", length = 255)
    private String organizationAddressLine1;

    @Column(name = "organization_address_line2", length = 255)
    private String organizationAddressLine2;

    @Column(name = "organization_city", length = 100)
    private String organizationCity;

    @Column(name = "organization_state", length = 100)
    private String organizationState;

    @Column(name = "organization_country", length = 100)
    private String organizationCountry;

    @Column(name = "organization_pin_code", length = 20)
    private String organizationPinCode;

    @Column(name = "organization_gst_no", length = 50)
    private String organizationGstNo;

    @Column(name = "organization_pan_no", length = 50)
    private String organizationPanNo;

    @Column(name = "organization_cin_number", length = 21)
    private String organizationCinNumber;

    @Column(name = "organization_email", length = 100)
    private String organizationEmail;

    @Column(name = "organization_phone", length = 50)
    private String organizationPhone;

    @Column(name = "organization_website", length = 500)
    private String organizationWebsite;

    @Column(name = "organization_logo_url", length = 255)
    private String organizationLogoUrl;

    // =====================================================
    // ORGANIZATION BANK SNAPSHOT
    // =====================================================

    @Column(name = "organization_bank_account_present")
    private Boolean organizationBankAccountPresent;

    @Column(name = "organization_account_holder_name", length = 255)
    private String organizationAccountHolderName;

    @Column(name = "organization_account_number", length = 50)
    private String organizationAccountNumber;

    @Column(name = "organization_ifsc_code", length = 20)
    private String organizationIfscCode;

    @Column(name = "organization_swift_code", length = 20)
    private String organizationSwiftCode;

    @Column(name = "organization_bank_name", length = 255)
    private String organizationBankName;

    @Column(name = "organization_bank_branch", length = 255)
    private String organizationBankBranch;

    @Column(name = "organization_upi_id", length = 100)
    private String organizationUpiId;

    @Column(name = "organization_payment_page_link", length = 500)
    private String organizationPaymentPageLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    private LocalDateTime rejectedAt;

    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "creditNote",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<CreditNoteInvoiceDetail> invoiceDetails = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String accountApprovalRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_approved_by")
    private User accountApprovedBy;

    private LocalDateTime accountApprovedAt;

    @Column(name = "gst_portal_attachment")
    private String gstPortalAttachment;

}