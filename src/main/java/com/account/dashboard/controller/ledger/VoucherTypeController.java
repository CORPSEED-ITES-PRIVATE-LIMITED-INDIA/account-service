package com.account.dashboard.controller.ledger;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.account.VoucherType;
import com.account.dashboard.service.VoucherTypeService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class VoucherTypeController {

	@Autowired
	VoucherTypeService voucherTypeService;
	
	@PostMapping(UrlsMapping.CREATE_VOUCHER_TYPE)
	public Boolean createVoucherType(@RequestParam String name){
		Boolean res=voucherTypeService.createVoucherType(name);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_VOUCHER_TYPE)
	public List<VoucherType> getAllVoucherType(){
		List<VoucherType> res=voucherTypeService.getAllVoucherType();	
		return res;
	}
	
	@PutMapping(UrlsMapping.UPDATE_VOUCHER_TYPE)
	public Boolean updateVoucherType(@RequestParam String name,@RequestParam Long id){
		Boolean res=voucherTypeService.updateVoucherType(name,id);	
		return res;
	}
}
