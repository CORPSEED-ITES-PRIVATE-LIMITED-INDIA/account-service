package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.dto.CreateLedgerTypeDto;
import com.account.dto.UpdateLedgerTypeDto;
import org.springframework.stereotype.Service;

import com.account.domain.account.LedgerType;

@Service
public interface LedgerTypeService {

	Boolean createLedgerType(CreateLedgerTypeDto createLedgerTypeDto);

	Boolean updateLedgerType(UpdateLedgerTypeDto updateLedgerTypeDto);

	List<LedgerType> getAllLedgerType();


	Boolean deleteLedgerType(Long id);

	Map<String,Object>  getAllLedgerTypeById(Long id);


	List<LedgerType> groupSearchApi(String name);

}
