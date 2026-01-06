package com.account.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface ProfitAndLossService {


	Map<String, Object> getAllProfit(String startDate, String endDate);

	Map<String, Object> getAllLoss(String startDate, String endDate);

	Map<String, Object> getAllProfitAndLoss(String startDate, String endDate);

}
