package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface CashFlowService {


	List<Map<String, Object>> getAllInFlow(String startDate, String endDate);

	List<Map<String, Object>> getAllOutFlow(String startDate, String endDate);

}
