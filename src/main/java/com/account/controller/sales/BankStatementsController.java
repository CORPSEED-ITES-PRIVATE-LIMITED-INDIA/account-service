package com.account.controller.sales;

import java.util.List;
import java.util.Map;

import com.account.dto.CreateBankStatementDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.domain.BankAccount;
import com.account.domain.BankStatement;
import com.account.service.BankStatementService;
import com.account.util.UrlsMapping;
@RestController
public class BankStatementsController {
     
    @Autowired
    BankStatementService bankStatementService;

	@PostMapping(UrlsMapping.CREATE_BANK_STATEMENTS)
	public BankStatement createBankStatements(@RequestBody CreateBankStatementDto createBankStatementDto){
		BankStatement res=bankStatementService.createBankStatements(createBankStatementDto);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_UNUSED_BANK_STATEMENTS)
	public List<Map<String,Object>> getUnUsedBankStatements(){
		List<Map<String,Object>> res=bankStatementService.getUnUsedBankStatements();	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_BANK_STATEMENTS)
	public List<Map<String,Object>> getAllBankStatements(){
		List<Map<String,Object>> res=bankStatementService.getAllBankStatements();	
		return res;
		
	}
	
	@PostMapping(UrlsMapping.ADD_REGISTER_AMOUNT_IN_BANK_STATEMENTS)
	public Boolean addRegisterAmountInBankStatement(@RequestParam Long bankstatementId,@RequestParam Long registerAmountId) throws Exception{
		Boolean res=bankStatementService.addRegisterAmountInBankStatement(bankstatementId,registerAmountId);	
		return res;
		
	}	
	@GetMapping(UrlsMapping.GET_ALL_BANK_ACCOUNTS)
	public List<BankAccount> getAllBankAccounts(){
		List<BankAccount> res=bankStatementService.getAllBankAccounts();	
		return res;
		
	}
}
