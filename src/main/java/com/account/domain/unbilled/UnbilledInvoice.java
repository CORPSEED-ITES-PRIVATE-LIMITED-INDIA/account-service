package com.account.domain.unbilled;

import com.account.domain.Contact;
import com.account.domain.PaymentReceipt;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.UnbilledStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "unbilled_invoice",
        indexes = {
                @Index(
                        name = "idx_unbilled_number_unique",
                        columnList = "unbilled_number",
                        unique = true
                ),
                @Index(
                        name = "idx_unbilled_public_uuid_unique",
                        columnList = "public_uuid",
                        unique = true
                ),
                @Index(
                        name = "idx_unbilled_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_unbilled_estimate_id_unique",
                        columnList = "estimate_id",
                        unique = true
                ),
                @Index(
                        name = "idx_unbilled_company_id",
                        columnList = "company_id"
                ),
                @Index(
                        name = "idx_unbilled_approved_by",
                        columnList = "approved_by"
                ),
                @Index(
                        name = "idx_unbilled_converted_advance_invoice",
                        columnList = "converted_to_advance_tax_invoice"
                )
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
        "unit",
        "contact",
        "payments",
        "taxInvoices",
        "createdBy",
        "updatedBy",
        "approvedBy"
})
public class UnbilledInvoice {

    private static final int MONEY_SCALE = 3;
    private static final int DOCUMENT_SCALE = 0;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_uuid",
            nullable = false,
            unique = true,
            length = 36
    )
    private String publicUuid;

    @Column(
            name = "unbilled_number",
            nullable = false,
            unique = true,
            length = 32
    )
    private String unbilledNumber;

    @Column(
            name = "advance_invoice_number",
            nullable = false,
            unique = true,
            length = 32
    )
    private String advanceInvoiceNumber;

    @Column(
            name = "advance_invoice_flag",
            nullable = false
    )
    private boolean advanceInvoiceFlag = false;

    @Column(
            name = "government_fee_active",
            nullable = false
    )
    private boolean governmentFeeActive = false;

    @Column(
            name = "tds_active",
            nullable = false
    )
    private boolean tdsActive = false;

    /**
     * Used only for the special conversion case:
     *
     * 1. UnbilledInvoice was created through an initial
     *    zero-value PURCHASE_ORDER.
     *
     * 2. The Operation Project was created and became
     *    eligible for billing/completed.
     *
     * 3. The customer later requested a Tax Invoice
     *    before making the actual payment.
     *
     * Once this flag becomes true, the old PO-created
     * UnbilledInvoice must not generate another Invoice
     * or receive future payment registrations.
     */
    @Column(
            name = "converted_to_advance_tax_invoice",
            nullable = false
    )
    private boolean convertedToAdvanceTaxInvoice = false;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "estimate_id",
            nullable = false,
            unique = true
    )
    private Estimate estimate;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "company_id",
            nullable = false
    )
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "gst_registration_type",
            nullable = false,
            length = 30
    )
    private GstRegistrationType gstRegistrationType =
            GstRegistrationType.REGISTERED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(
            name = "total_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal totalAmount = zeroMoney();

    /**
     * Accounts-approved settlement amount.
     */
    @Column(
            name = "received_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal receivedAmount = zeroMoney();

    /**
     * Pending settlement awaiting Accounts approval.
     */
    @Column(
            name = "current_received_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal currentReceivedAmount = zeroMoney();

    @Column(
            name = "outstanding_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal outstandingAmount = zeroMoney();

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            length = 30,
            nullable = false
    )
    private UnbilledStatus status =
            UnbilledStatus.PENDING_APPROVAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(
            name = "approval_remarks",
            columnDefinition = "TEXT"
    )
    private String approvalRemarks;

    @Column(
            name = "is_cancelled",
            nullable = false
    )
    private boolean isCancelled = false;

    @Column(
            name = "rejection_reason",
            columnDefinition = "TEXT"
    )
    private String rejectionReason;

    @OneToMany(
            mappedBy = "unbilledInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("createdAt ASC, id ASC")
    private List<PaymentReceipt> payments =
            new ArrayList<>();

    /**
     * Contains only payment-first Invoices.
     *
     * Advance Tax Invoices have:
     * invoice.unbilledInvoice = null
     */
    @OneToMany(
            mappedBy = "unbilledInvoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("createdAt ASC, id ASC")
    private List<Invoice> taxInvoices =
            new ArrayList<>();

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            updatable = false
    )
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(
            name = "cancel_attachment",
            columnDefinition = "TEXT"
    )
    private String cancelAttachment;

    @PrePersist
    protected void onCreate() {

        if (publicUuid == null
                || publicUuid.isBlank()) {

            publicUuid =
                    UUID.randomUUID().toString();
        }

        if (gstRegistrationType == null) {
            gstRegistrationType =
                    GstRegistrationType.REGISTERED;
        }

        if (status == null) {
            status =
                    UnbilledStatus.PENDING_APPROVAL;
        }

        normalizeMoneyFields();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeMoneyFields();
    }

    private void normalizeMoneyFields() {
        totalAmount = safeDocumentMoney(totalAmount);
        receivedAmount = safeMoney(receivedAmount);
        currentReceivedAmount = safeMoney(currentReceivedAmount);
        outstandingAmount = totalAmount
                .subtract(receivedAmount)
                .max(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    public GstRegistrationType getEffectiveGstRegistrationType() {
        return gstRegistrationType != null
                ? gstRegistrationType
                : GstRegistrationType.REGISTERED;
    }

    /**
     * Reserves a pending settlement amount.
     * The amount is Bank plus rounded TDS for domestic/SEZ payments,
     * and Bank only for International payments.
     */
    public void applyPayment(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Payment amount cannot be null");
        }

        BigDecimal safeAmount = safeMoney(amount);

        if (safeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Payment amount cannot be negative");
        }

        BigDecimal availableOutstanding = getAvailableOutstandingAmount();
        if (safeAmount.compareTo(availableOutstanding) > 0) {
            throw new IllegalArgumentException(
                    "Payment settlement cannot exceed available outstanding amount"
            );
        }

        currentReceivedAmount = safeMoney(currentReceivedAmount)
                .add(safeAmount)
                .setScale(MONEY_SCALE, ROUNDING_MODE);

        outstandingAmount = safeDocumentMoney(totalAmount)
                .subtract(safeMoney(receivedAmount))
                .max(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    /** Outstanding still available after pending payment reservations. */
    public BigDecimal getAvailableOutstandingAmount() {
        return safeMoney(outstandingAmount)
                .subtract(safeMoney(currentReceivedAmount))
                .max(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? zeroMoney()
                : value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    /** Final Unbilled total is rounded to a whole rupee and stored as x.000. */
    private static BigDecimal safeDocumentMoney(BigDecimal value) {
        if (value == null) {
            return zeroMoney();
        }
        return value
                .setScale(DOCUMENT_SCALE, ROUNDING_MODE)
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

}