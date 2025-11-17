package com.account.dashboard.controller.ledger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.dto.UpdateLedgerTypeDto;
import com.account.dashboard.service.LedgerTypeService;
import com.account.dashboard.util.*;

import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.service.TrialBalanceService;
import com.account.dashboard.util.UrlsMapping;

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
