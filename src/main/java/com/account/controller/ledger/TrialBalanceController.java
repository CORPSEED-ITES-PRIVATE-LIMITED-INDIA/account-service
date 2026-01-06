package com.account.controller.ledger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.account.service.TrialBalanceService;
import com.account.util.UrlsMapping;

@RestController
public class TrialBalanceController {
	
	@Autowired
	TrialBalanceService trialBalanceService;
	
	
	
	@GetMapping(UrlsMapping.GET_ALL_TRIAL_BALANCE)
	public List<Map<String,Object>> getAllTrialBalance(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		List<Map<String,Object>> res=trialBalanceService.getAllTrialBalance(startDate,endDate);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_TRIAL_BALANCE_DATA)
	public Map<String,Object> getAllTrialBalanceData(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		Map<String,Object> res=trialBalanceService.getAllTrialBalanceData(startDate,endDate);	
		return res;
	}
}
