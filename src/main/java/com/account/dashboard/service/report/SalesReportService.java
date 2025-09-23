package com.account.dashboard.service.report;

import java.util.List;
import java.util.Map;

public interface SalesReportService {

	List<Map<String, Object>> getAllSalesReport(int page, int size, String status);

}
