package com.account.dashboard.service.dashboard;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.dto.GraphDateFilter;

@Service
public interface SalesDashboardService {

	List<Map<String, Object>> getAllLeadMonthWiseV2(GraphDateFilter graphDateFilter);

	
}
