package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import com.account.dashboard.domain.InvoiceData;
import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.dto.CreateAmountDto;
import com.account.dashboard.dto.CreatePurchaseOrderDto;
import com.account.dashboard.dto.UpdatePaymentDto;

@Service
public interface PaymentRegisterService {

	PaymentRegister createPaymentRegister(CreateAmountDto createAmountDto);

	List<Map<String,Object>> getAllPaymentRegister(String status);

	Boolean updatePaymentRegister(UpdatePaymentDto updatePaymentDto);

	Boolean paymentApproveAndDisapproved(UpdatePaymentDto updatePaymentDto);

	PaymentRegister getPaymentRegisterById(long id);

	Boolean paymentApproveAndDisapprovedV3(Long paymentRegisterId, Long estimateId);

	List<PaymentRegister> getPaymentRegisterByEstimateId(long id);

	Boolean createInvoice(Long id);

	InvoiceData getInvoice(Long id);

	Map<String, Integer> leftAmount(Long id);

	List<PaymentRegister> getAllPaymentRegisterByStatus(String status);

	Boolean paymentDisapproved(Long id);

	Boolean allPaymentApprovedV3(Long paymentRegisterId);

	Boolean paymentApproveAndDisapprovedV4(Long paymentRegisterId, Long estimateId);

	List<InvoiceData> getAllInvoiceAccordingToUser(Long userId);

	List<InvoiceData> getAllInvoiceForSales(Long userId);

	PaymentRegister createPurchaseOrder(CreatePurchaseOrderDto createPurchaseOrderDto);

	List<Map<String,Object>> getAllPurchaseOrder(Long userId);

	List<Map<String, Object>> getAllPaymentRegisterWithCompany(Long userId);

	List<Map<String,Object>> getAllPaymentRegisterWithPage(int page, int size, String status);

	Long getAllPaymentRegisterCount(String status);

	List<PaymentRegister> getAllPaymentRegisterByUser( int page, int size,Long userId, String status);

	List<InvoiceData> getAllInvoice(Long userId, int page, int size);

	long getAllInvoiceCount(Long userId);

	Map<String, Object> getRemainingAmount(Long id);

	List<Map<String,Object>> searchPaymentRegister(String searchParam, String name,String fromDate,String toDate);


}
