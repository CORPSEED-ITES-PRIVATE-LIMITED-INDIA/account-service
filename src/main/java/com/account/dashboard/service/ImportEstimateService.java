package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.dto.GraphDateFilter;

@Service
public interface ImportEstimateService {

	List<Map<String, Object>> importEstimateData(GraphDateFilter graphDateFilter);

	List<Map<String, Object>> importUserData(GraphDateFilter graphDateFilter);
	
	

}
