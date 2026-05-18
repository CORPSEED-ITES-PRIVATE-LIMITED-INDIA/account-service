package com.account.dto.taxation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class GstTaxationReportDto {

    private Long totalInvoices;

    private BigDecimal totalTaxableAmount;
    private BigDecimal totalInvoiceAmount;

    private BigDecimal totalGstCollected;
    private BigDecimal totalCgstCollected;
    private BigDecimal totalSgstCollected;
    private BigDecimal totalIgstCollected;

    private BigDecimal averageGstPerInvoice;
    private BigDecimal averageInvoiceValue;
}