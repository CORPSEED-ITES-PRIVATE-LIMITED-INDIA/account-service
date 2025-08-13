package com.account.dashboard.controller.ledger;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.BankAccount;
import com.account.dashboard.domain.Organization;
import com.account.dashboard.dto.BankAccountDto;
import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.dto.OrganizationDto;
import com.account.dashboard.dto.StatutoryOrganizationDto;
import com.account.dashboard.util.UrlsMapping;
import com.account.dashboard.service.OrganizationService;
@RestController
public class OrganizationController {
    
	@Autowired
	OrganizationService organizationService;
	
	@PostMapping(UrlsMapping.CREATE_ORGANIIZATION)
	public Boolean createOrganization(@RequestBody OrganizationDto organizationDto) throws Exception{
		Boolean res=organizationService.createOrganization(organizationDto);	
		return res;
	}
	
	
	@GetMapping(UrlsMapping.GET_ORGANIIZATION_BY_ID)
	public Organization getOrganizationById(@RequestParam Long id){
		Organization res=organizationService.getOrganizationById(id);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_ORGANIIZATION)
	public List<Organization> getAllOrganization(){
		List<Organization> res=organizationService.getAllOrganization();	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_ORGANIIZATION_BY_NAME)
	public Organization getAllOrganizationByName(@RequestParam String name){
		Organization res=organizationService.getAllOrganizationByName(name);	
		return res;
	}
	
	@PostMapping(UrlsMapping.CREATE_STATUTORY_IN_ORGANIIZATION)
	public Boolean createStatutoryInOrganization(@RequestBody StatutoryOrganizationDto statutoryOrganizationDto) throws Exception{
		Boolean res=organizationService.createStatutoryInOrganization(statutoryOrganizationDto);	
		return res;
	}
	
	@PostMapping(UrlsMapping.ADD_BANK_ACCOUNT_IN_ORGANIIZATION)
	public Boolean addBankAccountInOrganization(@RequestBody BankAccountDto bankAccountDto) throws Exception{
		Boolean res=organizationService.addBankAccountInOrganization(bankAccountDto);	
		return res;
	}
	
	

	@GetMapping(UrlsMapping.GET_ALL_BANK_ACCOUNT_BY_ORGANIZATION)
	public List<BankAccount> getAllBankAccountByOrganization(@RequestParam Long organizationId ) throws Exception{
		List<BankAccount> res=organizationService.getAllBankAccountByOrganization(organizationId);	
		return res;
	}
	
}
