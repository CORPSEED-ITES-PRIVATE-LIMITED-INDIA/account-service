package com.account.dto.creditNote;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CreditNoteInvoiceDetailResponseDto {

    private Long id;

    private Long invoiceId;

    private String invoiceNumber;

    private LocalDate invoiceDate;

    private BigDecimal invoiceGrandTotal;

    private BigDecimal invoiceGstAmount;

    private BigDecimal invoiceCgstAmount;

    private BigDecimal invoiceSgstAmount;

    private BigDecimal invoiceIgstAmount;

    private String invoiceStatus;
}