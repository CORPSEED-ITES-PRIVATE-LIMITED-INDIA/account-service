package com.account.domain;

import com.account.domain.invoice.Invoice;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.status.PaymentStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
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
                @Index(
                        name = "idx_payment_receipt_unbilled_id",
                        columnList = "unbilled_invoice_id"
                ),
                @Index(
                        name = "idx_payment_receipt_invoice_id",
                        columnList = "invoice_id"
                ),
                @Index(
                        name = "idx_payment_receipt_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_payment_receipt_payment_date",
                        columnList = "payment_date"
                ),
                @Index(
                        name = "idx_payment_receipt_received_by",
                        columnList = "received_by"
                ),
                @Index(
                        name = "idx_payment_receipt_bank_ledger",
                        columnList = "bank_ledger_id"
                )
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

    /*
     * Bank transactions and payment settlement values
     * are maintained at 3 decimals.
     */
    private static final int MONEY_SCALE = 3;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_UP;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Normal payment source.
     *
     * Normal payment:
     *
     * unbilledInvoice != null
     * invoice == null
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unbilled_invoice_id")
    private UnbilledInvoice unbilledInvoice;

    /**
     * Advance Tax Invoice payment source.
     *
     * ATI payment:
     *
     * invoice != null
     * unbilledInvoice == null
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "payment_type_id",
            nullable = false
    )
    private PaymentType paymentType;

    /**
     * Actual amount received through Bank, Cash or Payment Gateway.
     *
     * This does not include TDS.
     *
     * Settlement:
     *
     * Domestic/SEZ = Bank amount + rounded TDS
     * International = Bank amount
     */
    @Column(
            name = "amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal amount = zeroMoney();

    /**
     * Nullable for initial zero-value Purchase Order registration.
     *
     * Required for every positive payment and validated in
     * PaymentServiceImpl.
     */
    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(
            name = "payment_mode",
            length = 50
    )
    private String paymentMode;

    @Column(
            name = "transaction_reference",
            length = 255
    )
    private String transactionReference;

    @Column(
            name = "remarks",
            columnDefinition = "TEXT"
    )
    private String remarks;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "received_by",
            nullable = false
    )
    private User receivedBy;

    @Column(
            name = "payment_proof",
            columnDefinition = "TEXT"
    )
    private String paymentProof;

    @Column(name = "payment_terms_days")
    private Integer paymentTermsDays;

    @Column(
            name = "payment_terms",
            length = 255
    )
    private String paymentTerms;

    @Column(
            name = "po_number",
            length = 255
    )
    private String poNumber;

    @Column(
            name = "po_attachment_url",
            columnDefinition = "TEXT"
    )
    private String poAttachmentUrl;

    /**
     * Null only for an initial zero-value Purchase Order registration.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_ledger_id")
    private LedgerMaster bankLedger;

    // =====================================================
    // EPR DETAILS
    // =====================================================

    @Column(
            name = "epr_financial_year",
            length = 20
    )
    private String eprFinancialYear;

    @Column(
            name = "epr_portal_registration_number",
            length = 255
    )
    private String eprPortalRegistrationNumber;

    @Column(
            name = "epr_certificate_or_invoice_number",
            length = 255
    )
    private String eprCertificateOrInvoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(
            name = "is_cancelled",
            nullable = false
    )
    private boolean isCancelled = false;

    @CreatedDate
    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Portion of the Bank receipt allocated to an Invoice or Unbilled.
     */
    @Column(
            name = "allocated_amount",
            nullable = false,
            precision = 19,
            scale = 3
    )
    private BigDecimal allocatedAmount = zeroMoney();

    /**
     * Remaining Bank receipt amount that has not been allocated.
     */
    @Column(
            name = "unallocated_amount",
            nullable = false,
            precision = 19,
            scale = 3
    )
    private BigDecimal unallocatedAmount = zeroMoney();

    @PrePersist
    protected void onCreate() {

        if (status == null) {
            status = PaymentStatus.PENDING;
        }

        normalizeFields();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeFields();
    }

    /**
     * Ensures all Bank and allocation values remain at 3 decimals.
     */
    private void normalizeFields() {

        this.amount =
                safeMoney(this.amount);

        this.allocatedAmount =
                safeMoney(this.allocatedAmount);

        this.unallocatedAmount =
                safeMoney(this.unallocatedAmount);

        if (this.paymentTermsDays != null
                && this.paymentTermsDays < 0) {

            throw new IllegalStateException(
                    "Payment terms days cannot be negative"
            );
        }

        if (this.amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Payment amount cannot be negative"
            );
        }

        if (this.allocatedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Allocated amount cannot be negative"
            );
        }

        if (this.unallocatedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Unallocated amount cannot be negative"
            );
        }
    }

    /**
     * Returns whether this receipt belongs to the
     * normal Estimate/Unbilled payment flow.
     */
    public boolean isUnbilledPayment() {

        return this.unbilledInvoice != null
                && this.invoice == null;
    }

    /**
     * Returns whether this receipt belongs to an
     * Advance Tax Invoice.
     */
    public boolean isAdvanceTaxInvoicePayment() {

        return this.invoice != null
                && this.unbilledInvoice == null;
    }

    /**
     * Returns whether this is an initial zero-value payment.
     *
     * This is mainly used for initial Purchase Order registration.
     */
    public boolean isZeroValueRegistration() {

        return safeMoney(this.amount)
                .compareTo(BigDecimal.ZERO) == 0;
    }

    private static BigDecimal safeMoney(
            BigDecimal value
    ) {

        return value == null
                ? zeroMoney()
                : value.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }

    private static BigDecimal zeroMoney() {

        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }
}