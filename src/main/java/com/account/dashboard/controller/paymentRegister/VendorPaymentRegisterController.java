package com.account.dashboard.controller.paymentRegister;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.VendorPaymentRegister;
import com.account.dashboard.dto.CreateAmountDto;
import com.account.dashboard.dto.CreateVendorAmountDto;
import com.account.dashboard.dto.VendorPaymentAddDto;
import com.account.dashboard.service.VendorPaymentRegisterServcie;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class VendorPaymentRegisterController {
	
	@Autowired
	VendorPaymentRegisterServcie vendorPaymentRegisterServcie;

	@PostMapping(UrlsMapping.CREATE_VENDOR_PAYMENT_REGISTER)
	public VendorPaymentRegister createVendorPaymentRegister(@RequestBody CreateVendorAmountDto createVendorAmountDto){
		VendorPaymentRegister res=vendorPaymentRegisterServcie.createVendorPaymentRegister(createVendorAmountDto);	
		return res;
		
	}
	// for Vendor 
	@GetMapping(UrlsMapping.GET_ALL_VENDOR_PAYMENT_REGISTER)
	public List<Map<String,Object>> getAllVendorPaymentRegister(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size){
		List<Map<String,Object>> res=vendorPaymentRegisterServcie.getAllVendorPaymentRegister(page-1,size);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_VENDOR_PAYMENT_REGISTER_COUNT)
	public int getAllVendorPaymentRegisterCount(){
		int res=vendorPaymentRegisterServcie.getAllVendorPaymentRegisterCount();	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_VENDOR_PAYMENT_REGISTER_FOR_ACCOUNT)
	public List<Map<String,Object>> getAllVendorPaymentRegisterForAccount(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "100") int size,String status){
		List<Map<String,Object>> res=vendorPaymentRegisterServcie.getAllVendorPaymentRegisterForAccount(page-1,size,status);	
		return res;
		
	}
	
	
	@GetMapping(UrlsMapping.GET_ALL_VENDOR_PAYMENT_REGISTER_FOR_ADMIN)
	public List<Map<String,Object>> getAllVendorPaymentRegisterForAdmin(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "100") int size,String status){
		List<Map<String,Object>> res=vendorPaymentRegisterServcie.getAllVendorPaymentRegisterForAdmin(page-1,size,status);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_VENDOR_PAYMENT_REGISTER_COUNT_FOR_ADMIN)
	public int getAllVendorPaymentRegisterCountForAccount(@RequestParam String status){
		int res=vendorPaymentRegisterServcie.getAllVendorPaymentRegisterCountForAccount(status);	
		return res;
		
	}
	@GetMapping(UrlsMapping.GET_ALL_VENDOR_PAYMENT_REGISTER_COUNT_FOR_ADMIN)
	public int getAllVendorPaymentRegisterCountForAdmin(@RequestParam String status){
		int res=vendorPaymentRegisterServcie.getAllVendorPaymentRegisterCountForAdmin(status);	
		return res;
		
	}
	@PutMapping(UrlsMapping.APPROVE_VENDOR_PAYMENT)
	public Boolean approveVendorPayment(@RequestParam Long currentUserId,@RequestParam String Status,@RequestParam Long id){
		Boolean res=vendorPaymentRegisterServcie.approveVendorPayment(currentUserId,Status,id);	
		return res;
		
	}
	@PutMapping(UrlsMapping.ADD_AMOUNT_BY_ACCOUNT_TEAM)
	public Boolean addAmountByAccountTeam(@RequestBody VendorPaymentAddDto  vendorPaymentAddDto){
		Boolean res=vendorPaymentRegisterServcie.addAmountByAccountTeam(vendorPaymentAddDto);	
		return res;
		
	}
	@GetMapping(UrlsMapping.GET_ALL_VENDOR_PAYMENT_REGISTER_HISTORY_BY_ID)
	public List<Map<String,Object>> getAllVendorPaymentRegisterHistoryById(@RequestParam Long id){
		List<Map<String,Object>> res=vendorPaymentRegisterServcie.getAllVendorPaymentRegisterHistoryById(id);	
		return res;
		
	}
}
