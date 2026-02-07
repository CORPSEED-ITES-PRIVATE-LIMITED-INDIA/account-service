package com.account.dto.invoice;

import com.account.domain.InvoiceStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class InvoiceFilterRequest {

    // 🔍 text search
    private String search;

    // 🎯 filters
    private InvoiceStatus status;

    // Invoice DATE (GST date)
    private LocalDate invoiceFromDate;
    private LocalDate invoiceToDate;

    // Created timestamp (audit)
    private LocalDate createdFromDate;
    private LocalDate createdToDate;

    // Amount filter
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    // Optional
    private Long solutionId;
}
