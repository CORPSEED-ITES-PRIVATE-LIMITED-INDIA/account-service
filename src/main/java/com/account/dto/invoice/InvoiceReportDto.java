package com.account.dto.invoice;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InvoiceReportDto {

    private Long totalInvoices;

    private BigDecimal totalRevenue;
    private BigDecimal totalNetRevenue;
    private BigDecimal totalGstCollected;

    private BigDecimal averageInvoiceValue;

    private BigDecimal totalUnbilledAmount;
    private BigDecimal totalReceivedAmount;
    private BigDecimal totalOutstandingAmount;
}