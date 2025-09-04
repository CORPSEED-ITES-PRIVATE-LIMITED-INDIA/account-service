package com.account.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.dto.CreateLedgerTypeDto;
import com.account.dashboard.dto.UpdateLedgerTypeDto;

@Service
public interface LedgerTypeService {

	Boolean createLedgerType(CreateLedgerTypeDto createLedgerTypeDto);

	Boolean updateLedgerType(UpdateLedgerTypeDto updateLedgerTypeDto);

	List<LedgerType> getAllLedgerType();


	Boolean deleteLedgerType(Long id);

	LedgerType getAllLedgerTypeById(Long id);


	List<LedgerType> groupSearchApi(String name);

}
