package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.dto.AddGstDto;

@Service
public interface GstDataCrmService {

	List<Map<String, Object>> getAllGstDataCrm(int i, int size);

	Boolean addGstDataCrm(AddGstDto addGstDto);

	Long getAllGstDataCrmCount();

	List<Map<String, Object>> getAllGstDataCrmForExport(String startDate, String endDate);

}
