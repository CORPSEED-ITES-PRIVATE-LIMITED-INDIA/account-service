package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

public interface InvoiceService {

	Map<String, Object> getInvoiceById(Long id);

	List<Map<String, Object>> getAllInvoiceForExport(String startDate,String endDate);

}
