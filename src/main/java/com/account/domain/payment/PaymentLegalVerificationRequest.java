package com.account.domain.payment;

import com.account.domain.PaymentReceipt;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.estimate.Estimate;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.enm.PaymentLegalVerificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_legal_verification_requests",
        indexes = {
                @Index(name = "idx_plvr_receipt", columnList = "payment_receipt_id"),
                @Index(name = "idx_plvr_unbilled", columnList = "unbilled_invoice_id"),
                @Index(name = "idx_plvr_status", columnList = "status")
        }
)
@Getter
@Setter
public class PaymentLegalVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * PO payment receipt for which Legal verification is required.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_receipt_id", nullable = false)
    private PaymentReceipt paymentReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unbilled_invoice_id", nullable = false)
    private UnbilledInvoice unbilledInvoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id")
    private Estimate estimate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    /*
     * This will be the PO agreement / PO document URL.
     * We will use PaymentRegistrationRequestDto.paymentProof for now.
     */
    @Column(name = "po_attachment_url", columnDefinition = "TEXT")
    private String poAttachmentUrl;

    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentLegalVerificationStatus status = PaymentLegalVerificationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_remark", columnDefinition = "TEXT")
    private String reviewRemark;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = PaymentLegalVerificationStatus.PENDING;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}