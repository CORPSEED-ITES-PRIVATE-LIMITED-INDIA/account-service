package com.account.dashboard.controller.ledger;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.account.Ledger;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.dto.CreateVoucherDto;
import com.account.dashboard.dto.LedgerDto;
import com.account.dashboard.service.VoucherService;
import com.account.dashboard.util.UrlsMapping;


@RestController
public class VoucherController {
	
	@Autowired
	VoucherService voucherService;
    
	
	@PostMapping(UrlsMapping.CREATE_VOUCHER)
	public Boolean createVoucher(@RequestBody CreateVoucherDto createVoucherDto){
		System.out.println("ghghgg name .."+createVoucherDto.getCompanyName());

		Boolean res=voucherService.createVoucher(createVoucherDto);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_VOUCHER)
	public List<Map<String,Object>> getAllVoucher(){
		List<Map<String,Object>> res=voucherService.getAllVoucherV2();	
		return res;
		
	}
	@GetMapping(UrlsMapping.GET_ALL_VOUCHER_TEST)
	public List<Voucher> getAllVoucherTest(){
		 List<Voucher> res = voucherService.getAllVoucher();	
		return res;
		
	}
	@GetMapping(UrlsMapping.GET_VOUCHER_AMOUNT)
	public Map<String,Object> getVoucherAmount(){
		Map<String,Object> res=voucherService.getVoucherAmount();	
		return res;
	}
	
	
	@GetMapping(UrlsMapping.GET_ALL_VOUCHER_BY_LEDGER_ID)
	public Map<String,Object> getAllVoucherByLedgerId(@RequestParam Long ledgerId){
		Map<String,Object> res=voucherService.getAllVoucherByLedgerId(ledgerId);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_VOUCHER_IN_BETWEEN_DATE)
	public Map<String,Object> getAllVoucherInBetween(@RequestParam String startDate,@RequestParam String endDate){
		Map<String,Object> res=voucherService.getAllVoucherInBetween(startDate,endDate);	
		return res;
	}
	
	@GetMapping(UrlsMapping.GET_ALL_VOUCHER_BY_GROUP)
	public Map<String,Object> getAllVoucherByGroup(@RequestParam Long id){
		Map<String,Object> res=voucherService.getAllVoucherByGroup(id);	
		return res;
	}
}
