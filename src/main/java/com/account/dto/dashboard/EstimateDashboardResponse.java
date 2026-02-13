package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimateDashboardResponse {
    private long totalEstimates;

    private BigDecimal totalRevenue;
    private BigDecimal totalSubTotal;
    private BigDecimal totalGst;

    private BigDecimal averageEstimateValue;

    private Map<String, Long> statusCount;
    private Map<String, BigDecimal> statusRevenue;

    private List<MonthlyTrendDto> monthlyTrend;
    private List<CompanyRevenueDto> topCompanies;

    private long sentCount;
    private long draftCount;
    private long approvedCount;



    // 🔥 NEW FIELDS
    private long totalUnbilledCount;
    private BigDecimal totalUnbilledAmount;

    private long totalInvoiceCount;
    private BigDecimal totalInvoicedAmount;

    private BigDecimal totalReceivedAmount;
    private BigDecimal totalOutstandingAmount;

    private double estimateToUnbilledConversionRate;
    private double collectionEfficiencyPercentage;

}
