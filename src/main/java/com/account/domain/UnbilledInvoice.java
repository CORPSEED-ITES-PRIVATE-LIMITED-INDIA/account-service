package com.account.domain;

import com.account.domain.estimate.Estimate;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "unbilled_invoice",
        indexes = {
                @Index(name = "idx_unbilled_number_unique", columnList = "unbilled_number", unique = true),
                @Index(name = "idx_unbilled_public_uuid_unique", columnList = "public_uuid", unique = true),
                @Index(name = "idx_unbilled_estimate_id_unique", columnList = "estimate_id", unique = true),
                @Index(name = "idx_unbilled_status", columnList = "status"),
                @Index(name = "idx_unbilled_company_id", columnList = "company_id"),
                @Index(name = "idx_unbilled_approved_by", columnList = "approved_by")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"estimate", "payments", "taxInvoices"})
public class UnbilledInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid;

    @Column(name = "unbilled_number", nullable = false, unique = true, length = 32)
    private String unbilledNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false, unique = true)
    private Estimate estimate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal receivedAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private UnbilledStatus status = UnbilledStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String approvalRemarks;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToMany(mappedBy = "unbilledInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentReceipt> payments = new ArrayList<>();

    @OneToMany(mappedBy = "unbilledInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Invoice> taxInvoices = new ArrayList<>();

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID().toString();
        }
    }

    public void applyPayment(BigDecimal amount) {
        this.receivedAmount = this.receivedAmount.add(amount);
        this.outstandingAmount = this.totalAmount.subtract(this.receivedAmount);
        if (this.outstandingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = UnbilledStatus.FULLY_PAID;
        } else if (this.receivedAmount.compareTo(BigDecimal.ZERO) > 0) {
            this.status = UnbilledStatus.PARTIALLY_PAID;
        }
    }
}