package com.account.domain;

import com.account.domain.invoice.Invoice;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.status.PaymentStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_receipt",
        indexes = {
                @Index(name = "idx_payment_receipt_unbilled_id", columnList = "unbilled_invoice_id"),
                @Index(name = "idx_payment_receipt_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_payment_receipt_status", columnList = "status"),
                @Index(name = "idx_payment_receipt_payment_date", columnList = "payment_date"),
                @Index(name = "idx_payment_receipt_received_by", columnList = "received_by"),
                @Index(name = "idx_payment_receipt_bank_ledger", columnList = "bank_ledger_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {
        "unbilledInvoice",
        "invoice",
        "paymentType",
        "receivedBy",
        "bankLedger"
})
public class PaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Existing payment-first source.
     * Normal payment: unbilledInvoice != null and invoice == null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unbilled_invoice_id")
    private UnbilledInvoice unbilledInvoice;

    /**
     * Advance Tax Invoice payment source.
     * Advance payment: invoice != null and unbilledInvoice == null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_type_id", nullable = false)
    private PaymentType paymentType;

    /** Actual amount received in Bank/Cash/Payment Gateway. */
    @Column(name = "amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /**
     * This is declared as String to match the service usage provided.
     * If your existing project uses a PaymentMode enum, preserve that enum type
     * and add @Enumerated(EnumType.STRING) instead.
     */
    @Column(name = "payment_mode", length = 50)
    private String paymentMode;

    @Column(name = "transaction_reference", length = 255)
    private String transactionReference;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by", nullable = false)
    private User receivedBy;

    @Column(name = "payment_proof", columnDefinition = "TEXT")
    private String paymentProof;

    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    @Column(name = "payment_terms", length = 255)
    private String paymentTerms;

    @Column(name = "po_number", length = 255)
    private String poNumber;

    @Column(name = "po_attachment_url", columnDefinition = "TEXT")
    private String poAttachmentUrl;

    /** Null only for an initial zero-value Purchase Order registration. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_ledger_id")
    private LedgerMaster bankLedger;

    // ==================== EPR DETAILS ====================

    @Column(name = "epr_financial_year", length = 20)
    private String eprFinancialYear;

    @Column(name = "epr_portal_registration_number", length = 255)
    private String eprPortalRegistrationNumber;

    @Column(name = "epr_certificate_or_invoice_number", length = 255)
    private String eprCertificateOrInvoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (paymentDate == null) {
            paymentDate = LocalDate.now();
        }
        amount = safeMoney(amount);
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        validateSourceMapping();
    }

    @PreUpdate
    protected void onUpdate() {
        amount = safeMoney(amount);
        validateSourceMapping();
    }

    private void validateSourceMapping() {
        boolean hasUnbilled = unbilledInvoice != null;
        boolean hasInvoice = invoice != null;

        if (hasUnbilled == hasInvoice) {
            throw new IllegalStateException(
                    "PaymentReceipt must reference exactly one source: "
                            + "either UnbilledInvoice or Advance Tax Invoice"
            );
        }
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isAdvanceInvoicePayment() {
        return invoice != null && unbilledInvoice == null;
    }

    public boolean isUnbilledPayment() {
        return unbilledInvoice != null && invoice == null;
    }
}
