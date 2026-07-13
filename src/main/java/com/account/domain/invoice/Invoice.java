package com.account.domain.invoice;

import com.account.domain.PaymentReceipt;
import com.account.domain.User;
import com.account.domain.company.GstRegistrationType;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "invoice",
        indexes = {
                @Index(
                        name = "idx_invoice_number_unique",
                        columnList = "invoice_number",
                        unique = true
                ),
                @Index(
                        name = "idx_invoice_public_uuid_unique",
                        columnList = "public_uuid",
                        unique = true
                ),
                @Index(
                        name = "idx_invoice_unbilled_id",
                        columnList = "unbilled_invoice_id"
                ),
                @Index(
                        name = "idx_invoice_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_invoice_date",
                        columnList = "invoice_date"
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
        "lineItems",
        "triggeringPayment"
})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public safe identifier for sharing (UUID v4)
    @Column(
            name = "public_uuid",
            nullable = false,
            unique = true,
            length = 36
    )
    private String publicUuid;

    @Column(
            name = "invoice_number",
            nullable = false,
            unique = true,
            length = 32
    )
    private String invoiceNumber;

    @Column(name = "solution_id", length = 500)
    private Long solutionId;

    @Column(
            name = "solution_name",
            nullable = false,
            length = 255
    )
    private String solutionName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "unbilled_invoice_id",
            nullable = false
    )
    private UnbilledInvoice unbilledInvoice;

    /*
     * GST registration type snapshot.
     *
     * This value is copied from UnbilledInvoice when the invoice
     * is generated. It protects historical invoices if the unit's
     * GST registration type changes later.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "gst_registration_type",
            nullable = false,
            length = 30
    )
    private GstRegistrationType gstRegistrationType =
            GstRegistrationType.REGISTERED;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate = LocalDate.now();

    // ==================== FINANCIALS ====================

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal subTotalExGst;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal totalGstAmount;

    // GST breakup - critical for GSTR-1 and customer visibility

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private InvoiceStatus status = InvoiceStatus.GENERATED;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    private boolean isCancelled = false;

    @OneToMany(
            mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_receipt_id")
    private PaymentReceipt triggeringPayment;

    @Column(
            name = "signed_qr_code",
            columnDefinition = "TEXT"
    )
    private String signedQrCode;

    // ==================== PLACE OF SUPPLY ====================

    @Column(length = 2)
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
    @JoinColumn(
            name = "created_by",
            updatable = false
    )
    @Comment("User who generated the invoice and registered the client payment")
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    @Comment("Accounts team member who verified and approved the payment")
    private User updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    // ==================== E-INVOICE DETAILS ====================

    @Column(
            name = "e_invoice_attachment_url",
            columnDefinition = "TEXT"
    )
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

    // ==================== GST HELPERS ====================

    public GstRegistrationType getEffectiveGstRegistrationType() {
        return gstRegistrationType != null
                ? gstRegistrationType
                : GstRegistrationType.REGISTERED;
    }

    public boolean isGstApplicable() {
        return getEffectiveGstRegistrationType()
                .isGstApplicable();
    }

    public boolean isZeroRatedSupply() {
        return getEffectiveGstRegistrationType()
                .isZeroRated();
    }
}