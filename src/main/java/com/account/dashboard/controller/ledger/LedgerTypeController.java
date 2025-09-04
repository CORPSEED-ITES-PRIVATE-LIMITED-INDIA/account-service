package com.account.dashboard.controller.ledger;

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

@RestController
public class LedgerTypeController {
	
	@Autowired
	LedgerTypeService ledgerService;
//isSubLeadger;
//	
//	boolean isDebitCredit;
//	
//	boolean isUsedForCalculation;
	@PostMapping(UrlsMapping.CREATE_LEDGER_TYPE)
	public Boolean createLedgerType(@RequestBody CreateLedgerTypeDto createLedgerTypeDto){
		Boolean res=ledgerService.createLedgerType(createLedgerTypeDto);	
		return res;
	}
	
	@PutMapping(UrlsMapping.UPDATE_LEDGER_TYPE)
	public Boolean updateLedgerType(@RequestBody  UpdateLedgerTypeDto updateLedgerTypeDto){
		Boolean res=ledgerService.updateLedgerType(updateLedgerTypeDto);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_LEDGER_TYPE)
	public List<LedgerType> getAllLedgerType(){
		List<LedgerType> res=ledgerService.getAllLedgerType();	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_LEDGER_TYPE_BY_ID)
	public LedgerType getAllLedgerTypeById(@RequestParam Long id){
		LedgerType res=ledgerService.getAllLedgerTypeById(id);	
		return res;
	}
	
	@GetMapping(UrlsMapping.DELETE_LEDGER_TYPE)
	public Boolean deleteLedgerType(@RequestParam Long id){
		Boolean res=ledgerService.deleteLedgerType(id);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GROUP_SEARCH_API)
	public List<LedgerType> groupSearchApi(@RequestParam String name){
		List<LedgerType> res=ledgerService.groupSearchApi(name);	
		return res;
	}
	
	
}
