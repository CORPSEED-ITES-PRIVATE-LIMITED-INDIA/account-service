package com.account.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.BankAccount;
import com.account.dashboard.domain.Organization;
import com.account.dashboard.dto.BankAccountDto;
import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.dto.OrganizationDto;
import com.account.dashboard.dto.StatutoryOrganizationDto;

@Service
public interface OrganizationService {

	Boolean createOrganization(OrganizationDto organizationDto) throws Exception;

	Organization getOrganizationById(Long id);

	List<Organization> getAllOrganization();

	Organization getAllOrganizationByName(String name);

	Boolean createStatutoryInOrganization(StatutoryOrganizationDto statutoryOrganizationDto);

	Boolean addBankAccountInOrganization(BankAccountDto bankAccountDto);

	List<BankAccount> getAllBankAccountByOrganization(Long organizationId);

}
