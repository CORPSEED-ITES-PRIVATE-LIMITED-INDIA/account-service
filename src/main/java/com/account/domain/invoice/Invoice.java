package com.account.domain.invoice;

import com.account.domain.PaymentReceipt;
import com.account.domain.User;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "invoice",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_invoice_advance_tax_invoice_request",
                        columnNames = "advance_tax_invoice_request_id"
                )
        },
        indexes = {
                @Index(name = "idx_invoice_number_unique", columnList = "invoice_number", unique = true),
                @Index(name = "idx_invoice_public_uuid_unique", columnList = "public_uuid", unique = true),
                @Index(name = "idx_invoice_estimate_id", columnList = "estimate_id"),
                @Index(name = "idx_invoice_unbilled_id", columnList = "unbilled_invoice_id"),
                @Index(name = "idx_invoice_advance_request_id", columnList = "advance_tax_invoice_request_id"),
                @Index(name = "idx_invoice_origin", columnList = "invoice_origin"),
                @Index(name = "idx_invoice_payment_status", columnList = "payment_status"),
                @Index(name = "idx_invoice_status", columnList = "status"),
                @Index(name = "idx_invoice_date", columnList = "invoice_date")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {
        "estimate",
        "unbilledInvoice",
        "advanceTaxInvoiceRequest",
        "lineItems",
        "triggeringPayment",
        "paymentReceipts",
        "createdBy",
        "updatedBy",
        "eInvoiceConfirmedBy"
})
public class Invoice {

    private static final int MONEY_SCALE = 3;
    private static final int DOCUMENT_SCALE = 0;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 32)
    private String invoiceNumber;

    /**
     * Direct Estimate link for both workflows.
     *
     * PAYMENT_APPROVAL invoice:
     * estimate != null, unbilledInvoice != null
     *
     * ADVANCE_TAX_INVOICE:
     * estimate != null, unbilledInvoice == null
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;

    @Column(name = "solution_id")
    private Long solutionId;

    @Column(name = "solution_name", nullable = false, length = 255)
    private String solutionName;

    /** Existing payment-first relation; null for Advance Tax Invoices. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unbilled_invoice_id")
    private UnbilledInvoice unbilledInvoice;

    /** Owning side. One approved request can produce only one Invoice. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advance_tax_invoice_request_id", unique = true)
    private AdvanceTaxInvoiceRequest advanceTaxInvoiceRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_origin", nullable = false, length = 30)
    private InvoiceOrigin invoiceOrigin = InvoiceOrigin.PAYMENT_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_registration_type", nullable = false, length = 30)
    private GstRegistrationType gstRegistrationType = GstRegistrationType.REGISTERED;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate = LocalDate.now();

    // ==================== FINANCIALS ====================

    @Column(name = "sub_total_ex_gst", precision = 19, scale = 3, nullable = false)
    private BigDecimal subTotalExGst = zeroMoney();

    @Column(name = "total_gst_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal totalGstAmount = zeroMoney();

    @Column(name = "cgst_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal cgstAmount = zeroMoney();

    @Column(name = "sgst_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal sgstAmount = zeroMoney();

    @Column(name = "igst_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal igstAmount = zeroMoney();

    @Column(name = "grand_total", precision = 19, scale = 3, nullable = false)
    private BigDecimal grandTotal = zeroDocumentTotal();

    /** Final Invoice total minus raw taxable plus GST total. */
    @Column(name = "round_off_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal roundOffAmount = zeroMoney();

    // ==================== PAYMENT SETTLEMENT ====================

    /** Accounts-approved settlement: bank amount + approved TDS amount. */
    @Column(name = "received_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal receivedAmount = zeroMoney();

    /** Settlement reserved by PENDING PaymentReceipts. */
    @Column(name = "pending_received_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal pendingReceivedAmount = zeroMoney();

    @Column(name = "outstanding_amount", precision = 19, scale = 3, nullable = false)
    private BigDecimal outstandingAmount = zeroMoney();

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private InvoicePaymentStatus paymentStatus = InvoicePaymentStatus.UNPAID;

    // ==================== INVOICE LIFECYCLE ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private InvoiceStatus status = InvoiceStatus.GENERATED;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "INR";

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled = false;

    @OneToMany(
            mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("displayOrder ASC, id ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    /** Existing payment that caused a payment-first Invoice to be generated. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_receipt_id")
    private PaymentReceipt triggeringPayment;

    /** Later payments linked to an Advance Tax Invoice. */
    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<PaymentReceipt> paymentReceipts = new ArrayList<>();

    @Column(name = "signed_qr_code", columnDefinition = "TEXT")
    private String signedQrCode;

    // ==================== PLACE OF SUPPLY ====================

    @Column(name = "place_of_supply_state_code", length = 2)
    private String placeOfSupplyStateCode;

    // ==================== BUYER GST DETAILS ====================

    @Column(name = "buyer_gstin", length = 15)
    private String buyerGstin;

    // ==================== ORGANIZATION SNAPSHOT ====================

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

    // ==================== AUDIT FIELDS ====================

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @Comment("User who generated the invoice")
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @Comment("User who last updated the invoice")
    private User updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== E-INVOICE DETAILS ====================

    @Column(name = "e_invoice_attachment_url", columnDefinition = "TEXT")
    private String eInvoiceAttachmentUrl;

    @Column(name = "e_invoice_irn", length = 100)
    private String eInvoiceIrn;

    @Column(name = "e_invoice_ack_no", length = 100)
    private String eInvoiceAckNo;

    @Column(name = "e_invoice_ack_date")
    private LocalDateTime eInvoiceAckDate;

    @Column(name = "e_invoice_confirmed_at")
    private LocalDateTime eInvoiceConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "e_invoice_confirmed_by")
    private User eInvoiceConfirmedBy;

    // ==================== OPERATION SYNC ====================

    @Column(name = "operation_synced", nullable = false)
    private boolean operationSynced = false;

    @Column(name = "operation_synced_at")
    private LocalDateTime operationSyncedAt;

    @Column(name = "operation_project_no", length = 100)
    private String operationProjectNo;


    @Column(name = "e_invoice_remarks", length = 1000)
    private String eInvoiceRemarks;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalized_by")
    private User finalizedBy;

    @Column(name = "finalization_remarks", length = 1000)
    private String finalizationRemarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_sync_status", length = 30)
    private OperationSyncStatus operationSyncStatus = OperationSyncStatus.PENDING;

    @Column(name = "operation_last_error", length = 1000)
    private String operationLastError;

    @Column(name = "operation_sync_attempts", nullable = false)
    private int operationSyncAttempts = 0;

    @Column(name = "operation_next_retry_at")
    private LocalDateTime operationNextRetryAt;

    // ==================== CALLBACKS ====================

    @PrePersist
    protected void onCreate() {
        if (publicUuid == null || publicUuid.isBlank()) {
            publicUuid = UUID.randomUUID().toString();
        }
        if (invoiceDate == null) {
            invoiceDate = LocalDate.now();
        }
        if (invoiceOrigin == null) {
            invoiceOrigin = InvoiceOrigin.PAYMENT_APPROVAL;
        }
        if (gstRegistrationType == null) {
            gstRegistrationType = GstRegistrationType.REGISTERED;
        }
        if (paymentStatus == null) {
            paymentStatus = InvoicePaymentStatus.UNPAID;
        }
        if (status == null) {
            status = InvoiceStatus.GENERATED;
        }
        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }
        normalizeMoneyFields();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeMoneyFields();
    }

    private void normalizeMoneyFields() {
        subTotalExGst = safeMoney(subTotalExGst);
        totalGstAmount = safeMoney(totalGstAmount);
        cgstAmount = safeMoney(cgstAmount);
        sgstAmount = safeMoney(sgstAmount);
        igstAmount = safeMoney(igstAmount);
        grandTotal = safeDocumentTotal(grandTotal);
        receivedAmount = safeMoney(receivedAmount);
        pendingReceivedAmount = safeMoney(pendingReceivedAmount);
        outstandingAmount = safeMoney(outstandingAmount);

        BigDecimal rawTotal = subTotalExGst
                .add(totalGstAmount)
                .setScale(MONEY_SCALE, ROUNDING_MODE);

        roundOffAmount = grandTotal
                .subtract(rawTotal)
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal zeroDocumentTotal() {
        return BigDecimal.ZERO.setScale(DOCUMENT_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? zeroMoney()
                : value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal safeDocumentTotal(BigDecimal value) {
        return value == null
                ? zeroDocumentTotal()
                : value.setScale(DOCUMENT_SCALE, ROUNDING_MODE);
    }

    // ==================== RELATIONSHIP HELPERS ====================

    public void addLineItem(InvoiceLineItem lineItem) {
        if (lineItem == null) {
            return;
        }
        lineItem.setInvoice(this);
        lineItems.add(lineItem);
    }

    public void removeLineItem(InvoiceLineItem lineItem) {
        if (lineItem == null) {
            return;
        }
        lineItems.remove(lineItem);
        lineItem.setInvoice(null);
    }

    // ==================== GST HELPERS ====================

    public GstRegistrationType getEffectiveGstRegistrationType() {
        return gstRegistrationType != null
                ? gstRegistrationType
                : GstRegistrationType.REGISTERED;
    }

    public boolean isGstApplicable() {
        return getEffectiveGstRegistrationType().isGstApplicable();
    }

    public boolean isZeroRatedSupply() {
        return getEffectiveGstRegistrationType().isZeroRated();
    }

    // ==================== PAYMENT HELPERS ====================

    public BigDecimal getAvailableOutstandingAmount() {
        return safeMoney(outstandingAmount)
                .subtract(safeMoney(pendingReceivedAmount))
                .max(BigDecimal.ZERO)
                .setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    public boolean isAdvanceTaxInvoice() {
        return invoiceOrigin == InvoiceOrigin.ADVANCE_TAX_INVOICE;
    }

    public boolean isPaymentApprovalInvoice() {
        return invoiceOrigin == InvoiceOrigin.PAYMENT_APPROVAL;
    }
}
