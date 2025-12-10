package com.account.dashboard.controller.importFeature;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.User;
import com.account.dashboard.dto.GraphDateFilter;
import com.account.dashboard.service.ImportEstimateService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class ImportEstimateController {
	
	
	@Autowired
	ImportEstimateService importEstimateService;
	
	@PostMapping(UrlsMapping.IMPORT_ESTIMATE_DATA)
	public List<Map<String,Object>> importEstimateData(@RequestParam("s3Url") String s3Url)
	{
		List<Map<String,Object>> alllead= importEstimateService.importEstimateData(s3Url);
		return alllead;
	}
	
	@PostMapping(UrlsMapping.IMPORT_USER_DATA)
	public List<User>importUserData(@RequestParam("s3Url") String s3Url)
	{
		List<User> alllead= importEstimateService.importUserData(s3Url);
		return alllead;
	}
	
	@PostMapping(UrlsMapping.IMPORT_PAYMENT_DATA)
	public List<PaymentRegister>importPaymentData(@RequestParam("s3Url") String s3Url)
	{
		List<PaymentRegister> alllead= importEstimateService.importPaymentData(s3Url);
		return alllead;
	}

}
