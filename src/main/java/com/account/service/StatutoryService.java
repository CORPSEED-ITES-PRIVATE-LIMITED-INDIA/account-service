package com.account.service;

import java.util.List;

import com.account.domain.account.LedgerType;
import com.account.dto.CreateStatutoryDetails;

public interface StatutoryService {

	Boolean createLedgerType(CreateStatutoryDetails createStatutoryDetails);

	Boolean updateStatutoryDetails(CreateStatutoryDetails createStatutoryDetails);

	LedgerType getStatutoryDetailsById(Long id);

	List<LedgerType> getAllStatutoryDetailsById(Long currentUserId);

}
