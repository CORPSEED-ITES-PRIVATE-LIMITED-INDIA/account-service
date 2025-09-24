package com.account.dashboard.controller.account;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.account.dashboard.service.InvoiceService;
import com.account.dashboard.util.UrlsMapping;

public class InvoiceController {

	
	@Autowired
	InvoiceService invoiceService;
	
	
	
	@GetMapping(UrlsMapping.GET_INVOICE_BY_ID)
	public Map<String,Object> getInvoiceById(@RequestParam(required = false) Long id){
		Map<String,Object> res=invoiceService.getInvoiceById(id);	
		return res;
		
	}
	
}
