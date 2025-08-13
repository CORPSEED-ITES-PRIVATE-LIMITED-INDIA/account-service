package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.BankStatement;
import com.account.dashboard.dto.CreateBankStatementDto;

@Service
public interface BankStatementService {

	BankStatement createBankStatements(CreateBankStatementDto createBankStatementDto);

	List<Map<String, Object>> getUnUsedBankStatements();

	Boolean addRegisterAmountInBankStatement(Long bankstatementId, Long registerAmountId) throws Exception;

	List<Map<String, Object>> getAllBankStatements();

}
