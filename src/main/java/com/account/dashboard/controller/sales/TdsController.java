package com.account.dashboard.controller.sales;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.BankStatement;
import com.account.dashboard.domain.TdsDetail;
import com.account.dashboard.dto.CreateBankStatementDto;
import com.account.dashboard.dto.CreateTdsDto;
import com.account.dashboard.service.BankStatementService;
import com.account.dashboard.service.TdsService;
import com.account.dashboard.util.UrlsMapping;
@RestController
public class TdsController {
    @Autowired
    TdsService tdsService;

	@PostMapping(UrlsMapping.CREATE_TDS)
	public TdsDetail createTds(@RequestBody CreateTdsDto createTdsDto){
		TdsDetail res=tdsService.createTds(createTdsDto);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_TDS)
	public List<TdsDetail> getAllTds(){
		List<TdsDetail> res=tdsService.getAllTds();	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_TDS_COUNT)
	public Map<String,Object> getAllTdsCount(){
		Map<String,Object> res=tdsService.getAllTdsCount();	
		return res;
		
	}
}
