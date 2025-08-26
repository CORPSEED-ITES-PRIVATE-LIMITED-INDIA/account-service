package com.account.dashboard.controller.ledger;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.service.CashFlowService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class CashFlowController {

	@Autowired
	CashFlowService cashFlowService;
	
	@GetMapping(UrlsMapping.GET_ALL_IN_FLOW)
	public List<Map<String,Object>> getAllInFlow(){
		List<Map<String,Object>> res=cashFlowService.getAllInFlow();	
		return res;
	}
	@GetMapping(UrlsMapping.GET_ALL_OUT_FLOW)
	public List<Map<String,Object>> getAllOutFlow(){
		List<Map<String,Object>> res=cashFlowService.getAllOutFlow();	
		return res;
	}
}
