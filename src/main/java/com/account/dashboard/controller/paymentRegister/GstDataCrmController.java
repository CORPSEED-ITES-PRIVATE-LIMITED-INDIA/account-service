package com.account.dashboard.controller.paymentRegister;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.service.GstDataCrmService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class GstDataCrmController {

	@Autowired
	GstDataCrmService gstDataCrmService;
	
	@GetMapping(UrlsMapping.GET_ALL_GST_DATA_CRM)
	public List<Map<String,Object>> getAllGstDataCrm(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size){
		List<Map<String,Object>> res=gstDataCrmService.getAllGstDataCrm(page-1,size);	
		return res;
		
	}
}
