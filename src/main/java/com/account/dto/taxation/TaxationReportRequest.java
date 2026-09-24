package com.account.dto.taxation;

import com.account.domain.status.InvoiceStatus;
import com.account.domain.status.TdsStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TaxationReportRequest {

    private TaxationReportType type;

    // Common filters
    private Long createdById;

    // GST invoice status filter
    private InvoiceStatus status;

    // TDS status filter
    private TdsStatus tdsStatus;

    // Date filters
    private LocalDate fromInvoiceDate;
    private LocalDate toInvoiceDate;

    private LocalDate fromCreatedDate;
    private LocalDate toCreatedDate;

    // Company filter
    private Long companyId;
    private String companyName;

    // Solution filter - GST from invoice.solutionId, TDS from estimate if available
    private Long solutionId;

    // Amount filters
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    // GST filter
    private Boolean includeGstOnly;

    // Currency filter - GST only
    private String currency;

    // Outstanding filter
    private Boolean onlyWithOutstanding;
}