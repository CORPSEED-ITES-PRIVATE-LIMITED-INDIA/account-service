package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.dto.CreateBankStatementDto;
import org.springframework.stereotype.Service;

import com.account.domain.BankAccount;
import com.account.domain.BankStatement;

@Service
public interface BankStatementService {

	BankStatement createBankStatements(CreateBankStatementDto createBankStatementDto);

	List<Map<String, Object>> getUnUsedBankStatements();

	Boolean addRegisterAmountInBankStatement(Long bankstatementId, Long registerAmountId) throws Exception;

	List<Map<String, Object>> getAllBankStatements();

	List<BankAccount> getAllBankAccounts();

}
