package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.domain.account.Ledger;
import com.account.dto.LedgerDto;
import com.account.dto.UpdateLedgerDto;
import org.springframework.stereotype.Service;

@Service
public interface LedgerService {

	Boolean createLedger(LedgerDto ledgerDto);

	List<Ledger> getAllLedger(int page, int size);

	Boolean updateLadger(UpdateLedgerDto updateLedgerDto);

	Ledger getLedgerById(Long id);

	List<Ledger> globalSearchLedger(String name);

	List<Ledger> getAllLedgerByGroupId(Long id);

	Map<String, Object> getAllAmountByGroupId(Long id);

	Map<String, Object> getAllAmountByLedgerId(Long id);

	long getAllLedgerCount();


}
