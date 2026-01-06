package com.account.service;

import java.util.List;

import com.account.dto.BankAccountDto;
import com.account.dto.OrganizationDto;
import com.account.dto.StatutoryOrganizationDto;
import org.springframework.stereotype.Service;

import com.account.domain.BankAccount;
import com.account.domain.Organization;

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
