package com.account.dashboard.service;

import java.util.List;

import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.dto.CreateStatutoryDetails;

public interface StatutoryService {

	Boolean createLedgerType(CreateStatutoryDetails createStatutoryDetails);

	Boolean updateStatutoryDetails(CreateStatutoryDetails createStatutoryDetails);

	LedgerType getStatutoryDetailsById(Long id);

	List<LedgerType> getAllStatutoryDetailsById(Long currentUserId);

}
