package com.account.domain;

import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.estimate.Estimate;
import com.account.domain.status.GovernmentFeeStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "government_fee",
        indexes = {
                @Index(name = "idx_gov_fee_public_uuid_unique", columnList = "public_uuid", unique = true),
                @Index(name = "idx_gov_fee_estimate_id_unique", columnList = "estimate_id", unique = true),
                @Index(name = "idx_gov_fee_unbilled_id_unique", columnList = "unbilled_invoice_id", unique = true),
                @Index(name = "idx_gov_fee_company_id", columnList = "company_id"),
                @Index(name = "idx_gov_fee_status", columnList = "status"),
                @Index(name = "idx_gov_fee_reference_unique", columnList = "fee_reference_number", unique = true)
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"estimate", "unbilledInvoice", "company", "createdBy", "updatedBy"})
public class GovernmentFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estimate_id", nullable = false, unique = true)
    private Estimate estimate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unbilled_invoice_id", nullable = false, unique = true)
    private UnbilledInvoice unbilledInvoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(name = "fee_reference_number", length = 50, unique = true)
    private String feeReferenceNumber;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "fee_type", length = 100)
    private String feeType;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal receivedAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal outstandingAmount = BigDecimal.ZERO;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private GovernmentFeeStatus status = GovernmentFeeStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String remarks;

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
    public void prePersist() {
        if (this.publicUuid == null || this.publicUuid.isBlank()) {
            this.publicUuid = UUID.randomUUID().toString();
        }
        recalculateOutstanding();
    }

    @PreUpdate
    public void preUpdate() {
        recalculateOutstanding();
    }

    public void recalculateOutstanding() {
        BigDecimal total = this.totalAmount == null ? BigDecimal.ZERO : this.totalAmount;
        BigDecimal received = this.receivedAmount == null ? BigDecimal.ZERO : this.receivedAmount;

        if (received.compareTo(total) > 0) {
            throw new IllegalArgumentException("Government fee received amount cannot be greater than total amount");
        }

        this.outstandingAmount = total.subtract(received);
    }
}