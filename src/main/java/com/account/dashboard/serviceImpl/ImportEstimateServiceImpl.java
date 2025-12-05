package com.account.dashboard.serviceImpl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.dto.GraphDateFilter;
import com.account.dashboard.service.ImportEstimateService;

@Service
public class ImportEstimateServiceImpl implements ImportEstimateService{

	@Override
	public List<Map<String, Object>> importEstimateData(GraphDateFilter graphDateFilter) {
		
		return null;
	}

	@Override
	public List<Map<String, Object>> importUserData(GraphDateFilter graphDateFilter) {
		// TODO Auto-generated method stub
		return null;
	}

}
