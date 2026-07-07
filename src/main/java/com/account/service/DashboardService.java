package com.account.service;

import com.account.dto.dashboard.*;

import java.time.LocalDate;

public interface DashboardService {

    TopSellingServicesResponseDto getTopSellingServices(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    );

    TopConvertedLeadsResponseDto getTopConvertedLeads(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    );

    RevenueCardsResponseDto getRevenueCards(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate
    );

    RevenueTrendResponseDto getRevenueTrend(
            Long userId,
            Integer months
    );

    RevenueByServiceResponseDto getRevenueByService(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    );

    TopCompaniesResponseDto getTopCompanies(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    );

    PaymentSummaryResponseDto getPaymentSummary(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate
    );
}