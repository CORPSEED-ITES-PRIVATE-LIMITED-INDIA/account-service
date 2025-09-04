package com.account.dashboard.controller.ledger;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.dto.CreateStatutoryDetails;
import com.account.dashboard.service.StatutoryService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class StatutoryController {
	
	@Autowired
	StatutoryService statutoryService;

	@PostMapping(UrlsMapping.ADD_STATUTORY_DETAILS)
	public Boolean addStatutoryDetails(@RequestBody CreateStatutoryDetails createStatutoryDetails){
		Boolean res=statutoryService.createLedgerType(createStatutoryDetails);	
		return res;
	}
	
	@PutMapping(UrlsMapping.UPDATE_STATUTORY_DETAILS)
	public Boolean updateStatutoryDetails(@RequestBody CreateStatutoryDetails createStatutoryDetails){
		Boolean res=statutoryService.updateStatutoryDetails(createStatutoryDetails);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_STATUTORY_DETAILS)
	public LedgerType getStatutoryDetailsById(@RequestParam Long id){
		LedgerType res=statutoryService.getStatutoryDetailsById(id);	
		return res;
	}
	
	
	@GetMapping(UrlsMapping.GET_ALL_STATUTORY_DETAILS)
	public List<LedgerType> getAllStatutoryDetailsById(@RequestParam Long currentUserId){
		List<LedgerType> res=statutoryService.getAllStatutoryDetailsById(currentUserId);	
		return res;
	}
}
