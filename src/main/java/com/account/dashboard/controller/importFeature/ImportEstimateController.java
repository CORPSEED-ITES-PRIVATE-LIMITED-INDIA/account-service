package com.account.dashboard.controller.importFeature;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.dto.GraphDateFilter;
import com.account.dashboard.service.ImportEstimateService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class ImportEstimateController {
	
	
	@Autowired
	ImportEstimateService importEstimateService;
	
	@PostMapping(UrlsMapping.IMPORT_ESTIMATE_DATA)
	public List<Map<String,Object>> importEstimateData(@RequestBody GraphDateFilter graphDateFilter)
	{
		List<Map<String,Object>> alllead= importEstimateService.importEstimateData(graphDateFilter);
		return alllead;
	}
	
	@PostMapping(UrlsMapping.IMPORT_USER_DATA)
	public List<Map<String,Object>> importUserData(@RequestBody GraphDateFilter graphDateFilter)
	{
		List<Map<String,Object>> alllead= importEstimateService.importUserData(graphDateFilter);
		return alllead;
	}
}
