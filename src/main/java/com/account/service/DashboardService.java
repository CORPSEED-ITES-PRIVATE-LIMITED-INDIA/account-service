package com.account.service;

import com.account.dto.dashboard.RevenueCardsResponseDto;
import com.account.dto.dashboard.RevenueTrendResponseDto;
import com.account.dto.dashboard.TopConvertedLeadsResponseDto;
import com.account.dto.dashboard.TopSellingServicesResponseDto;

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

    RevenueTrendResponseDto getRevenueTrend(Long userId, Integer months);


    RevenueCardsResponseDto getRevenueCards(Long userId, String period, LocalDate fromDate, LocalDate toDate);
}