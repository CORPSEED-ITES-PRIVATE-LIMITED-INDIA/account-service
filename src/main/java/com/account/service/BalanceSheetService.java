package com.account.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface BalanceSheetService {

	Map<String,Object> getAllBalanceSheetLiabilities(String startDate, String endDate);

	Map<String,Object> getAllBalanceSheetAssets(String startDate, String endDate);

	List<Map<String, Object>> getAllBalanceSheetAssetsForExport(String startDate, String endDate);

	List<Map<String, Object>> getAllBalanceSheetLiabilitiesForExport(String startDate, String endDate);

	List<Map<String, Object>> getAllGroupByParentGroupId(String startDate, String endDate);

	Map<String, Object> getLiabilitiesSubGroupByGroup(String startDate, String endDate, String name);

	Map<String, Object> getAssetsSubGroupByGroup(String startDate, String endDate, String name);

	List<Map<String, Object>> getAllAssetsAndLiabilities();

}
