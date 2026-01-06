package com.account.controller.account;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.service.InvoiceService;
import com.account.util.UrlsMapping;
@RestController
public class InvoiceController {

	
	@Autowired
	InvoiceService invoiceService;
	
	
	@GetMapping(UrlsMapping.GET_INVOICE_BY_ID)
	public Map<String,Object> getInvoiceById(@RequestParam(required = false) Long id){
		Map<String,Object> res=invoiceService.getInvoiceById(id);	
		return res;
		
	}
	@GetMapping(UrlsMapping.GET_ALL_INVOICE_FOR_EXPORT)
	public List<Map<String,Object>> getAllInvoiceForExport(@RequestParam(required=false) String startDate,@RequestParam(required=false) String endDate){
		List<Map<String,Object>>res=invoiceService.getAllInvoiceForExport(startDate,endDate);	
		return res;		
	}
	
	@GetMapping(UrlsMapping.GET_INVOICE_BY_UNBILLED_ID)
	public Map<String,Object> getInvoiceByUnbilledId(@RequestParam(required = false) Long unbilledId){
		Map<String,Object> res=new HashMap<>();
//		Map<String,Object> res=invoiceService.getInvoiceByUnbilledId(unbilledId);	
		return res;
		
	}
	
}
