package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.dto.AddGstDto;

@Service
public interface GstDataCrmService {

	List<Map<String, Object>> getAllGstDataCrm(int page, int size,String startDate, String endDate);

	Boolean addGstDataCrm(AddGstDto addGstDto);

	Long getAllGstDataCrmCount(String startDate, String endDate);

	List<Map<String, Object>> getAllGstDataCrmForExport(String startDate, String endDate);

}
