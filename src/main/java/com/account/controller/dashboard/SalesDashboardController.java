package com.account.controller.dashboard;

import java.util.List;
import java.util.Map;

import com.account.dto.GraphDateFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.account.service.dashboard.SalesDashboardService;
import com.account.util.UrlsMapping;


@RestController
public class SalesDashboardController {
	
	
	@Autowired
	SalesDashboardService salesDashboardService;
	
	
	@PostMapping(UrlsMapping.GET_SALES_DASHBOARD_REVENUE_MONTHLY)
	public List<Map<String,Object>> getAllLeadMonthWise(@RequestBody GraphDateFilter graphDateFilter)
	{
		List<Map<String,Object>> alllead= salesDashboardService.getAllLeadMonthWiseV2(graphDateFilter);
		return alllead;
	}
	
	@PostMapping(UrlsMapping.GET_SALES_DASHBOARD_REVENUE_BY_USER)
	public List<Map<String,Object>> getSalesDashboardRevenueByUser(@RequestBody GraphDateFilter graphDateFilter)
	{
		List<Map<String,Object>> alllead= salesDashboardService.getSalesDashboardRevenueByUser(graphDateFilter);
		return alllead;
	}
	
	@PostMapping(UrlsMapping.GET_SALES_DASHBOARD_REVENUE_BY_COMPANY)
	public List<Map<String,Object>> getSalesDashboardRevenueByCompany(@RequestBody GraphDateFilter graphDateFilter)
	{
		List<Map<String,Object>> alllead= salesDashboardService.getSalesDashboardRevenueByCompany(graphDateFilter);
		return alllead;
	}
	
	@PostMapping(UrlsMapping.GET_SALES_DASHBOARD_REVENUE_BY_SERVICE)
	public List<Map<String,Object>> getSalesDashboardRevenueByService(@RequestBody GraphDateFilter graphDateFilter)
	{
		List<Map<String,Object>> alllead= salesDashboardService.getSalesDashboardRevenueByService(graphDateFilter);
		return alllead;
	}

}
