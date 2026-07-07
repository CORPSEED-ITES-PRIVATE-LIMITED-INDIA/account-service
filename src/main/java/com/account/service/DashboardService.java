package com.account.service;

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
}