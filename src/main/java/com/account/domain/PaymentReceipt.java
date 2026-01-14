package com.account.domain;

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

@Entity
@Table(name = "payment_receipt",
        indexes = {
                @Index(name = "idx_payment_unbilled_id", columnList = "unbilled_invoice_id"),
                @Index(name = "idx_payment_date", columnList = "payment_date"),
                @Index(name = "idx_payment_type_id", columnList = "payment_type_id"),
                @Index(name = "idx_payment_transaction_ref", columnList = "transaction_ref")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"unbilledInvoice"})
public class PaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unbilled_invoice_id", nullable = false)
    private UnbilledInvoice unbilledInvoice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_type_id", nullable = false)
    private PaymentType paymentType;  // FULL, PARTIAL, INSTALLMENT, PURCHASE_ORDER

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(length = 50)
    private String paymentMode;  // UPI, NEFT, RTGS, CASH, CARD, CHEQUE, etc.

    @Column(name = "transaction_ref", length = 100)
    private String transactionReference;  // Bank txn ID, UPI ID, Cheque No.

    @Column(columnDefinition = "TEXT")
    private String remarks;  // Salesperson notes: "Paid for Milestone 1", "Advance payment"

    // Who registered this payment (usually salesperson)
    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by", updatable = false)
    private User receivedBy;

    // Future: Proof attachment (file path or S3 key)
    @Column(length = 500)
    private String proofDocumentUrl;

    @Column(length = 9)
    private String eprFinancialYear;

    @Column(length = 50)
    private String eprPortalRegistrationNumber;

    @Column(length = 100)
    private String eprCertificateOrInvoiceNumber;


    // Auditing
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;


}