package com.account.service;

import java.util.List;

import com.account.domain.ManageSales;
import com.account.dto.CreateAccountData;
import com.account.dto.UpdateAccountData;
import org.springframework.stereotype.Service;

import com.account.domain.BankAccount;

@Service
public interface AccountService {


	ManageSales getAccountData(Long accountId);

	List<ManageSales> getAllAccountData();
	
	ManageSales DeleteAccountData(Long manageSalesId);

	ManageSales createAccountData(CreateAccountData createAccountData);

	ManageSales updateAccountData(UpdateAccountData updateAccountData);

	BankAccount getCompanyAccountData();

}
