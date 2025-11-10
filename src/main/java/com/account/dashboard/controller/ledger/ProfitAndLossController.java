package com.account.dashboard.controller.ledger;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.service.ProfitAndLossService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class ProfitAndLossController {
	
	@Autowired
	ProfitAndLossService profitAndLossService;
	
	@GetMapping(UrlsMapping.GET_ALL_PROFIT)
	public Map<String, Object> getAllProfit(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		Map<String, Object> res=profitAndLossService.getAllProfit(startDate,endDate);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_LOSS)
	public Map<String, Object> getAllLoss(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		Map<String, Object> res=profitAndLossService.getAllLoss(startDate,endDate);	
		return res;
	}
	
//	GET_ALL_PROFIT_AND_LOSS
	@GetMapping(UrlsMapping.GET_ALL_PROFIT_AND_LOSS)
	public Map<String, Object> getAllProfitAndLoss(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		Map<String, Object> res=profitAndLossService.getAllProfitAndLoss(startDate,endDate);	
		return res;
	}

}
