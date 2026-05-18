package com.account.domain.creditNote;

import com.account.domain.Company;
import com.account.domain.Contact;
import com.account.domain.UnbilledInvoice;
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

    @Column(name = "total_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "received_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal receivedAmount;

    @Column(name = "current_received_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal currentReceivedAmount;

    @Column(name = "outstanding_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal outstandingAmount;

    @Column(name = "refund_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditNoteStatus status;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String approvalRemarks;

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
}