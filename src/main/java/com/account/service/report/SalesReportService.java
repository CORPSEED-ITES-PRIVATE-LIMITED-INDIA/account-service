package com.account.service.report;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.RequestParam;

public interface SalesReportService {

	List<Map<String, Object>> getAllSalesReport(int page, int size, String status,String startDate, String endDate);

	Long getAllSalesReportCount(String status,String startDate, String endDate);

	List<Map<String, Object>> getAllSalesReportForExport(String status,String startDate, String endDate);

}
