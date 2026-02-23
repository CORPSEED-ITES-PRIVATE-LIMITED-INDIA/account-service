package com.account.domain;

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
import java.util.UUID;

@Entity
@Table(name = "invoice",
        indexes = {
                @Index(name = "idx_invoice_number_unique", columnList = "invoice_number", unique = true),
                @Index(name = "idx_invoice_public_uuid_unique", columnList = "public_uuid", unique = true),
                @Index(name = "idx_invoice_unbilled_id", columnList = "unbilled_invoice_id"),
                @Index(name = "idx_invoice_status", columnList = "status"),
                @Index(name = "idx_invoice_date", columnList = "invoice_date")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"unbilledInvoice", "lineItems", "triggeringPayment"})
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public safe identifier for sharing (UUID v4)
    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 32)
    private String invoiceNumber; // e.g. INV-2026-00009876

    @Column(name = "solution_id", length = 500)
    private Long solutionId;

    @Column(name = "solution_name", nullable = false, length = 255)
    private String solutionName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unbilled_invoice_id", nullable = false)
    private UnbilledInvoice unbilledInvoice;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate = LocalDate.now();

    // Financials
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal subTotalExGst;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalGstAmount;

    // GST breakup - critical for GSTR-1 & customer visibility
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal grandTotal;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private InvoiceStatus status = InvoiceStatus.GENERATED;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_receipt_id")
    private PaymentReceipt triggeringPayment;

    @Column(name = "signed_qr_code", columnDefinition = "TEXT")
    private String signedQrCode; // QR code with digital signature

    // === Place of Supply (for GST split logic) ===
    @Column(length = 2)
    private String placeOfSupplyStateCode; // e.g. "06" for Haryana

    // === Reference GSTINs ===
    @Column(name = "buyer_gstin", length = 15)
    private String buyerGstin;

    // Auditing
    /**
     * The user who originally created/generated the invoice.
     *
     * Typically, this is the salesperson or staff member who:
     * - Interacts with the client
     * - Requests or collects the payment
     * - Registers the payment in the system
     *
     * This field is automatically populated by Spring Data auditing (@CreatedBy)
     * and is not updatable after creation.
     */
    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    @Comment("User who generated the invoice and registered the client payment")
    private User createdBy;


    /**
     * The user who last modified/approved the invoice or payment record.
     *
     * Typically, this is a member of the accounts or finance team who:
     * - Verifies the payment details
     * - Approves the payment after validation
     * - Updates the record if required
     *
     * This field is automatically populated by Spring Data auditing (@LastModifiedBy)
     * whenever the entity is updated.
     */
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

}