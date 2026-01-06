package com.account.controller.ledger;

import java.util.List;

import com.account.dto.CreateBankDetailsDto;
import com.account.dto.UpdateBankDetailsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.account.domain.BankDetails;
import com.account.service.BankDetailsService;
import com.account.util.UrlsMapping;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

@Controller
public class BankDetailsController {
	
	
	@Autowired
	BankDetailsService bankDetailsService ;

	
	@PostMapping(UrlsMapping.CREATE_BANK_DETAILS)
	public Boolean createBankDetails(@RequestBody CreateBankDetailsDto createBankDetailsDto){
		Boolean res=bankDetailsService.createBankDetails(createBankDetailsDto);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_BANK_DETAILS)
	public List<BankDetails> getAllBankDetails(){
		List<BankDetails> res=bankDetailsService.getAllBankDetails();	
		return res;
	}
	
	@PostMapping(UrlsMapping.UPDATE_BANK_DETAILS)
	public BankDetails updateBankDetails(@RequestBody UpdateBankDetailsDto updateBankDetailsDto){
		BankDetails res=bankDetailsService.updateBankDetails(updateBankDetailsDto);	
		return res;
	}
//	
	
}
