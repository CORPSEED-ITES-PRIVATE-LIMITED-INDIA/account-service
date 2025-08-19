package com.account.dashboard.controller.sales;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.InvoiceData;
import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.dto.CreateAccountData;
import com.account.dashboard.dto.CreateAmountDto;
import com.account.dashboard.dto.CreatePurchaseOrderDto;
import com.account.dashboard.dto.UpdatePaymentDto;
import com.account.dashboard.service.PaymentRegisterService;
import com.account.dashboard.util.UrlsMapping;


@RestController
public class PaymentRegisterController {
	
	@Autowired
	PaymentRegisterService paymentRegisterService;

	@PostMapping(UrlsMapping.CREATE_PAYMENT_REGISTER)
	public PaymentRegister createPaymentRegister(@RequestBody CreateAmountDto createAmountDto){
		PaymentRegister res=paymentRegisterService.createPaymentRegister(createAmountDto);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_PAYMENT_REGISTER)
	public List<PaymentRegister> getAllPaymentRegister(@RequestParam(required = false) String status){
		List<PaymentRegister> res=paymentRegisterService.getAllPaymentRegister(status);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_PAYMENT_REGISTER_WITH_PAGE)
	public List<PaymentRegister> getAllPaymentRegisterWithPage(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size,@RequestParam String status){
		List<PaymentRegister> res=paymentRegisterService.getAllPaymentRegisterWithPage(page-1,size,status);	
		return res;
		
	}
	
	@PutMapping(UrlsMapping.UPDATE_PAYMENT_REGISTER)
	public Boolean updatePaymentRegister(@RequestBody UpdatePaymentDto updatePaymentDto){
		Boolean res=paymentRegisterService.updatePaymentRegister(updatePaymentDto);	
		return res;
		
	}
	
	
	@GetMapping(UrlsMapping.PAYMENT_APPROVE)
	public Boolean paymentApproveAndDisapproved(@RequestBody UpdatePaymentDto updatePaymentDto){
		Boolean res=paymentRegisterService.paymentApproveAndDisapproved(updatePaymentDto);

		return res;
		
	}
	
	@PutMapping(UrlsMapping.PAYMENT_DISSAPPROVE)
	public Boolean paymentDisapproved(@RequestParam Long id){
		Boolean res=paymentRegisterService.paymentDisapproved(id);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_PAYMENT_REGISTER_BY_ID)
	public PaymentRegister getPaymentRegisterById(@RequestParam long id){
		PaymentRegister res=paymentRegisterService.getPaymentRegisterById(id);	
		return res;
		
	}
	
//	@PutMapping(UrlsMapping.UPDATE_PAYMENT_REGISTER)
//	public Boolean paymentAppro(@RequestBody UpdatePaymentDto updatePaymentDto){
//		Boolean res=paymentRegisterService.updatePaymentRegister(updatePaymentDto);	
//		return res;
//		
//	}
//	
	@PutMapping(UrlsMapping.PAYMENT_APPROVE_V2)
	public Boolean paymentApproveAndDisapprovedv2(@RequestParam Long paymentRegisterId ,@RequestParam Long estimateId){
		Boolean res=paymentRegisterService.paymentApproveAndDisapprovedV4(paymentRegisterId,estimateId);	
		return res;
		
	}
	
	
	@GetMapping(UrlsMapping.GET_PAYMENT_REGISTER_BY_ESTIMATE_ID)
	public List<PaymentRegister> getPaymentRegisterByEstimateId(@RequestParam long id){
		List<PaymentRegister> res=paymentRegisterService.getPaymentRegisterByEstimateId(id);	
		return res;
		
	}
	
	@PutMapping(UrlsMapping.CREATE_INVOICE)
	public Boolean createInvoice(@RequestParam Long id){
		Boolean res=paymentRegisterService.createInvoice(id);	
		return res;
		
	}
	
	
	@PutMapping(UrlsMapping.GET_INVOICE)
	public InvoiceData getInvoice(@RequestParam Long id){
		InvoiceData res=paymentRegisterService.getInvoice(id);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.LEFT_AMOUNT)
	public Map<String,Integer> leftAmount(@RequestParam Long id){
		Map<String,Integer> res=paymentRegisterService.leftAmount(id);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_PAYMENT_REGISTER_BY_STATUS)
	public List<PaymentRegister> getAllPaymentRegisterByStatus(@RequestParam String status){
		List<PaymentRegister> res=paymentRegisterService.getAllPaymentRegisterByStatus(status);	
		return res;
		
	}
	
	@PutMapping(UrlsMapping.PAYMENT_APPROVE_V3)
	public Boolean paymentApproveAndDisapprovedv3(@RequestParam Long paymentRegisterId ,@RequestParam Long estimateId){
		Boolean res=paymentRegisterService.allPaymentApprovedV3(paymentRegisterId);	
		return res;
		
	}
	
	
	@GetMapping(UrlsMapping.GET_ALL_INVOICE_ACCORDING_TO_USER)
	public List<InvoiceData> getAllInvoiceAccordingToUser(@RequestParam Long userId){
		List<InvoiceData> res=paymentRegisterService.getAllInvoiceAccordingToUser(userId);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_INVOICE)
	public List<InvoiceData> getAllInvoice(@RequestParam Long userId,@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size){
		List<InvoiceData> res=paymentRegisterService.getAllInvoice(userId,page-1,size);	
		return res;		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_INVOICE_COUNT)
	public long getAllInvoiceCount(@RequestParam Long userId){
		long res=paymentRegisterService.getAllInvoiceCount(userId);	
		return res;		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_INVOICE_FOR_MANAGE_SALES)
	public List<InvoiceData> getAllInvoiceForSales(@RequestParam Long userId){
		List<InvoiceData> res=paymentRegisterService.getAllInvoiceForSales(userId);	
		return res;
		
	}
	
	@PostMapping(UrlsMapping.CREATE_PURCHASE_ORDER)
	public PaymentRegister createPurchaseOrder(@RequestBody CreatePurchaseOrderDto createPurchaseOrderDto){
		PaymentRegister res=paymentRegisterService.createPurchaseOrder(createPurchaseOrderDto);	
		return res;
		
	}
	
	@PostMapping(UrlsMapping.GET_ALL_PURCHASE_ORDER)
	public List<Map<String,Object>> getAllPurchaseOrder(@RequestParam Long userId){
		List<Map<String,Object>> res=paymentRegisterService.getAllPurchaseOrder(userId);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_PAYMENT_REGISTER_WITH_COMPANY)
	public List<Map<String,Object>> getAllPaymentRegisterWithCompany(@RequestParam(required = false) Long userId){
		List<Map<String,Object>> res=paymentRegisterService.getAllPaymentRegisterWithCompany(userId);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_PAYMENT_REGISTER_COUNT)
	public Long getAllPaymentRegisterCount(@RequestParam(required = false) String status){
		Long res=paymentRegisterService.getAllPaymentRegisterCount(status);	
		return res;
		
	}
	
	@GetMapping(UrlsMapping.GET_ALL_PAYMENT_REGISTER_BY_USER)
	public List<PaymentRegister> getAllPaymentRegisterByUser(@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "size", defaultValue = "10") int size,@RequestParam Long userId, @RequestParam(required = false) String status){
		List<PaymentRegister> res=paymentRegisterService.getAllPaymentRegisterByUser(page,size,userId,status);	
		return res;
		
	}
}
