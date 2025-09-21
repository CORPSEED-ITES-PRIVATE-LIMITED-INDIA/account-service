package com.account.dashboard.controller.dashboard;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.dto.GraphDateFilter;
import com.account.dashboard.service.dashboard.SalesDashboardService;
import com.account.dashboard.util.UrlsMapping;


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
	
	

}
