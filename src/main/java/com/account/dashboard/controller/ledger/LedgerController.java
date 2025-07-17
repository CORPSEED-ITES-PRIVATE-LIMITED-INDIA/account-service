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

import com.account.dashboard.domain.account.Ledger;
import com.account.dashboard.dto.LedgerDto;
import com.account.dashboard.dto.UpdateLedgerDto;
import com.account.dashboard.service.LedgerService;
import com.account.dashboard.util.UrlsMapping;


@RestController
public class LedgerController {

	
	@Autowired
	LedgerService ledgerService;

	
	@PostMapping(UrlsMapping.CREATE_LEDGER)
	public Boolean createLedger(@RequestBody LedgerDto ledgerDto){
		Boolean res=ledgerService.createLedger(ledgerDto);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_LEDGER)
	public List<Ledger> getAllLedger(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size){
		List<Ledger> res=ledgerService.getAllLedger(page-1,size);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_LEDGER_COUNT)
	public long getAllLedgerCount(){
		long res=ledgerService.getAllLedgerCount();	
		return res;
	}
	
	@PutMapping(UrlsMapping.UPDATE_LEDGER)
	public Boolean updateLadger(@RequestBody UpdateLedgerDto updateLedgerDto){
		Boolean res=ledgerService.updateLadger(updateLedgerDto );	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_LEDGER_BY_ID)
	public Ledger getLedgerById(@RequestParam Long id){
		Ledger res=ledgerService.getLedgerById(id);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GLOBAL_SEARCH_LEDGER)
	public List<Ledger> globalSearchLedger(@RequestParam String name){
		List<Ledger> res=ledgerService.globalSearchLedger(name);	
		return res;
	}
	
	
	@GetMapping(UrlsMapping.GET_ALL_LEDGER_BY_GROUP_ID)
	public List<Ledger> getAllLedgerByGroupId(@RequestParam Long id){
		List<Ledger> res=ledgerService.getAllLedgerByGroupId(id);	
		return res;
	}
	
	
	@GetMapping(UrlsMapping.GET_ALL_AMOUNT_BY_GROUP_ID)
	public Map<String,Object> getAllAmountByGroupId(@RequestParam Long id){
		Map<String,Object> res=ledgerService.getAllAmountByGroupId(id);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_AMOUNT_BY_LEDGER_ID)
	public Map<String,Object> getAllAmountByLedgerId(@RequestParam Long id){
		Map<String,Object> res=ledgerService.getAllAmountByLedgerId(id);	
		return res;
	}
}
