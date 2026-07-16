package com.account.domain;

import com.account.domain.company.Company;
import com.account.domain.estimate.Estimate;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.TdsStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tds_registration",
        indexes = {
                @Index(name = "idx_tds_registration_public_uuid", columnList = "public_uuid", unique = true),
                @Index(name = "idx_tds_registration_estimate_id", columnList = "estimate_id"),
                @Index(name = "idx_tds_registration_company_id", columnList = "company_id"),
                @Index(name = "idx_tds_registration_unbilled_id", columnList = "unbilled_invoice_id"),
                @Index(name = "idx_tds_registration_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_tds_registration_payment_receipt_id", columnList = "payment_receipt_id"),
                @Index(name = "idx_tds_registration_status", columnList = "status"),
                @Index(name = "idx_tds_registration_deleted", columnList = "is_deleted")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {
        "estimate",
        "company",
        "unbilledInvoice",
        "invoice",
        "paymentReceipt",
        "createdBy",
        "updatedBy"
})
public class TdsRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /** Existing payment-first TDS source. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unbilled_invoice_id")
    private UnbilledInvoice unbilledInvoice;

    /** Advance Tax Invoice payment TDS source. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_receipt_id", nullable = false)
    private PaymentReceipt paymentReceipt;

    @Column(name = "tds_percentage", precision = 5, scale = 2, nullable = false)
    private BigDecimal tdsPercentage;

    @Column(name = "taxable_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal taxableAmount;

    @Column(name = "tds_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal tdsAmount;

    /** Assigned during Accounts approval. */
    @Column(name = "tds_date")
    private LocalDate tdsDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TdsStatus status = TdsStatus.PENDING;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (publicUuid == null || publicUuid.isBlank()) {
            publicUuid = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = TdsStatus.PENDING;
        }
        normalizeMoneyFields();
        validateSourceMapping();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeMoneyFields();
        validateSourceMapping();
    }

    private void normalizeMoneyFields() {
        tdsPercentage = safeMoney(tdsPercentage);
        taxableAmount = safeMoney(taxableAmount);
        tdsAmount = safeMoney(tdsAmount);
    }

    private void validateSourceMapping() {
        boolean hasUnbilled = unbilledInvoice != null;
        boolean hasInvoice = invoice != null;

        if (hasUnbilled == hasInvoice) {
            throw new IllegalStateException(
                    "TdsRegistration must reference exactly one source: "
                            + "either UnbilledInvoice or Advance Tax Invoice"
            );
        }
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isAdvanceInvoiceTds() {
        return invoice != null && unbilledInvoice == null;
    }

    public boolean isUnbilledTds() {
        return unbilledInvoice != null && invoice == null;
    }


}
