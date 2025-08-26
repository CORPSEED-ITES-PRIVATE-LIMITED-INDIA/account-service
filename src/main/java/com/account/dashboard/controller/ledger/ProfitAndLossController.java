package com.account.dashboard.controller.ledger;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.service.ProfitAndLossService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class ProfitAndLossController {
	
	@Autowired
	ProfitAndLossService profitAndLossService;
	
	@GetMapping(UrlsMapping.GET_ALL_PROFIT)
	public List<Map<String,Object>> getAllProfit(){
		List<Map<String,Object>> res=profitAndLossService.getAllProfit();	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_LOSS)
	public List<Map<String,Object>> getAllLoss(){
		List<Map<String,Object>> res=profitAndLossService.getAllLoss();	
		return res;
	}
	

}
