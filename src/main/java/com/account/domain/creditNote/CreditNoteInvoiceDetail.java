package com.account.domain.creditNote;

import com.account.domain.Invoice;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "credit_note_invoice_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditNoteInvoiceDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    private Long invoiceId;

    private String invoiceNumber;

    private LocalDate invoiceDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal invoiceGrandTotal;

    @Column(precision = 15, scale = 2)
    private BigDecimal invoiceGstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal invoiceCgstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal invoiceSgstAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal invoiceIgstAmount;

    private String invoiceStatus;
}