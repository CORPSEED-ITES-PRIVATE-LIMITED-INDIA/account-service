 package com.account.controller.report;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.service.report.SalesReportService;
import com.account.util.UrlsMapping;

@RestController
public class SalesReportController {
	

	@Autowired
	SalesReportService salesReportService;
	
	@GetMapping(UrlsMapping.GET_ALL_SALES_REPORT)
	public List<Map<String,Object>> getAllSalesReport(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size,@RequestParam String status,@RequestParam String startDate,@RequestParam String endDate){
		List<Map<String,Object>>res=salesReportService.getAllSalesReport(page-1,size,status,startDate,endDate);	
		return res;
		
	}
	@GetMapping(UrlsMapping.GET_ALL_SALES_REPORT_COUNT)
	public Long getAllSalesReportCount(@RequestParam String status,@RequestParam String startDate,@RequestParam String endDate){
		Long res=salesReportService.getAllSalesReportCount(status,startDate,endDate);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_SALES_REPORT_FOR_EXPORT)
	public List<Map<String,Object>> getAllSalesReportForExport(@RequestParam String status,@RequestParam String startDate,@RequestParam String endDate){
		List<Map<String,Object>>res=salesReportService.getAllSalesReportForExport(status,startDate,endDate);	
		return res;
		
	}

}
