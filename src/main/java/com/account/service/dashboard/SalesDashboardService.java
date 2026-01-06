package com.account.service.dashboard;

import java.util.List;
import java.util.Map;

import com.account.dto.GraphDateFilter;
import org.springframework.stereotype.Service;

@Service
public interface SalesDashboardService {

	List<Map<String, Object>> getAllLeadMonthWiseV2(GraphDateFilter graphDateFilter);

	List<Map<String, Object>> getSalesDashboardRevenueByCompany(GraphDateFilter graphDateFilter);

	List<Map<String, Object>> getSalesDashboardRevenueByUser(GraphDateFilter graphDateFilter);

	List<Map<String, Object>> getSalesDashboardRevenueByService(GraphDateFilter graphDateFilter);

	
}
