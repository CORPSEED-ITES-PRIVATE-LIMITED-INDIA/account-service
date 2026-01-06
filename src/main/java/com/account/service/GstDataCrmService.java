package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.dto.AddGstDto;
import org.springframework.stereotype.Service;

@Service
public interface GstDataCrmService {

	List<Map<String, Object>> getAllGstDataCrm(int page, int size,String startDate, String endDate);

	Boolean addGstDataCrm(AddGstDto addGstDto);

	Long getAllGstDataCrmCount(String startDate, String endDate);

	List<Map<String, Object>> getAllGstDataCrmForExport(String startDate, String endDate);

	Boolean updateGstClaimAmount(Long id, double amount, String documents);

}
