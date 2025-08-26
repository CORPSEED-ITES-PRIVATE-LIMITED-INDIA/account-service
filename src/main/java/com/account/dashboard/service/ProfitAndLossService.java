package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface ProfitAndLossService {

	List<Map<String, Object>> getAllProfit();

	List<Map<String, Object>> getAllLoss();

}
