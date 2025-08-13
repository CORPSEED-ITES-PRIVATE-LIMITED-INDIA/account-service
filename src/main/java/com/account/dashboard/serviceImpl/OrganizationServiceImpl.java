package com.account.dashboard.serviceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.BankAccount;
import com.account.dashboard.domain.Organization;
import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.dto.BankAccountDto;
import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.dto.OrganizationDto;
import com.account.dashboard.dto.StatutoryOrganizationDto;
import com.account.dashboard.repository.BankAccountRepository;
import com.account.dashboard.repository.OrganizationRepository;
import com.account.dashboard.service.OrganizationService;


@Service
public class OrganizationServiceImpl implements OrganizationService{

	@Autowired
	OrganizationRepository  organizationRepository;
	
	@Autowired
	BankAccountRepository bankAccountRepository;

	@Override
	public Boolean createOrganization(OrganizationDto organizationDto) throws Exception {
		Boolean flag=false;
		Organization organization=organizationRepository.findByName(organizationDto.getName());
		if(organization==null) {
			organization = new Organization();
			organization.setName(organizationDto.getName());
			organization.setJoiningDate(new Date());
			organization.setPin(organizationDto.getPin());
			organization.setAddress(organizationDto.getAddress());
			organization.setState(organizationDto.getState());
			organization.setCountry(organizationDto.getCountry());
			organization.setPin(organizationDto.getPin());
			organization.setDeleted(false);
			organizationRepository.save(organization);
		}else {
			throw new Exception("Already Exit");
		}
		flag=true;
		return flag;
	}

	@Override
	public Organization getOrganizationById(Long id) {
		Organization organization = organizationRepository.findById(id).get();
		return organization;
	}

	@Override
	public List<Organization> getAllOrganization() {
		List<Organization> organizationList = organizationRepository.findAll();
		return organizationList;
	}

	@Override
	public Organization getAllOrganizationByName(String name) {
		Organization organization = organizationRepository.findByName(name);
		return organization;
	}

	@Override
	public Boolean createStatutoryInOrganization(StatutoryOrganizationDto statutoryOrganizationDto) {
		Boolean flag=false;
		Organization organization = organizationRepository.findById(statutoryOrganizationDto.getId()).get();
		organization.setHsnSacPresent(statutoryOrganizationDto.isHsnSacPresent());
		organization.setHsnSacDetails(statutoryOrganizationDto.getHsnSacDetails());
		organization.setHsnDescription(statutoryOrganizationDto.getHsnDescription());
		organization.setHsnSacData(statutoryOrganizationDto.getHsnSacData());
		organization.setClassification(statutoryOrganizationDto.getClassification());

		organization.setGstRateDetailPresent(statutoryOrganizationDto.isGstRateDetailPresent());
		organization.setGstRateDetails(statutoryOrganizationDto.getGstRateDetails());
		organization.setTaxabilityType(statutoryOrganizationDto.getTaxabilityType());
		organization.setGstRatesData(statutoryOrganizationDto.getGstRatesData());

		organization.setBankAccountPresent(statutoryOrganizationDto.isBankAccountPresent());
		organization.setAccountHolderName(statutoryOrganizationDto.getAccountHolderName());
		organization.setAccountNo(statutoryOrganizationDto.getAccountNo());
		organization.setIfscCode(statutoryOrganizationDto.getIfscCode());
		organization.setSwiftCode(statutoryOrganizationDto.getSwiftCode());
		organization.setBankName(statutoryOrganizationDto.getBankName());
		organization.setBranch(statutoryOrganizationDto.getBranch());

		organizationRepository.save(organization);
		flag=true;
		return flag;
	}

	@Override
	public Boolean addBankAccountInOrganization(BankAccountDto bankAccountDto) {
		Boolean flag = false;
		Organization organization = organizationRepository.findById(bankAccountDto.getBankAccountId()).get();
		BankAccount bankAccount = new BankAccount();
		bankAccount.setAccountHolderName(bankAccountDto.getAccountHolderName());
		bankAccount.setAccountNo(bankAccountDto.getAccountNo());
		bankAccount.setBranch(bankAccountDto.getBranch());
		bankAccount.setIfscCode(bankAccountDto.getIfscCode());
		bankAccount.setSwiftCode(bankAccountDto.getSwiftCode());
		bankAccountRepository.save(bankAccount);
		
		List<BankAccount>list = organization.getOrganizationBankAccount();
		list.add(bankAccount);
		organization.setOrganizationBankAccount(list);
		organizationRepository.save(organization);
		flag=true;
		return flag;
	}

	@Override
	public List<BankAccount> getAllBankAccountByOrganization(Long organizationId) {
		List<BankAccount> bankAccountList = organizationRepository.findById(organizationId).get().getOrganizationBankAccount();
	    return bankAccountList;
	}

}
