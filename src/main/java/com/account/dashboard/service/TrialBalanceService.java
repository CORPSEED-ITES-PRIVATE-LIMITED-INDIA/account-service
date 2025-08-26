package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface TrialBalanceService {

	List<Map<String, Object>> getAllTrialBalance();

}
