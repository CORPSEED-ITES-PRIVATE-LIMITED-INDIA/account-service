package com.account.dashboard.controller.paymentRegister;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.dto.AddGstDto;
import com.account.dashboard.service.GstDataCrmService;
import com.account.dashboard.util.UrlsMapping;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

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
	@GetMapping(UrlsMapping.ADD_GST_DATA_CRM)
	public Boolean addGstDataCrm(@RequestBody AddGstDto addGstDto){
		Boolean res=gstDataCrmService.addGstDataCrm(addGstDto);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_GST_DATA_CRM_COUNT)
	public Long getAllGstDataCrmCount(){
		Long res=gstDataCrmService.getAllGstDataCrmCount();	
		return res;
		
	}
}
