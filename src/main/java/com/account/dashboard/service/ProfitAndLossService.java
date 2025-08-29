package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface ProfitAndLossService {


	List<Map<String, Object>> getAllProfit(String startDate, String endDate);

	List<Map<String, Object>> getAllLoss(String startDate, String endDate);

}
