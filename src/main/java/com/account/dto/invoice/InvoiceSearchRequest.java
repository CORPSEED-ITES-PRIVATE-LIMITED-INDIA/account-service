package com.account.dto.invoice;

import com.account.domain.InvoiceStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceSearchRequest {

    // Pagination
    private Integer page = 0;
    private Integer size = 20;

    // Core filters
    private Long createdById;
    private InvoiceStatus status;

    // Date filters
    private LocalDate fromInvoiceDate;
    private LocalDate toInvoiceDate;

    private LocalDate fromCreatedDate;
    private LocalDate toCreatedDate;

    // Company filter
    private Long companyId;
    private String companyName;

    // Solution filter
    private Long solutionId;

    // Amount filters
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    // GST filter
    private Boolean includeGstOnly;

    // Currency
    private String currency;

    // Due filter
    private Boolean onlyWithOutstanding;

}