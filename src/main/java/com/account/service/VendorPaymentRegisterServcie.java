package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.dto.CreateVendorAmountDto;
import com.account.dto.CreateVendorAmountManualDto;
import com.account.dto.VendorPaymentAddDto;
import org.springframework.stereotype.Service;

import com.account.domain.VendorPaymentRegister;

@Service
public interface VendorPaymentRegisterServcie {

	VendorPaymentRegister createVendorPaymentRegister(CreateVendorAmountDto createVendorAmountDto);

	List<Map<String, Object>> getAllVendorPaymentRegister(int page, int size);

	int getAllVendorPaymentRegisterCount();

	List<Map<String, Object>> getAllVendorPaymentRegisterForAccount(int page,int size,String status);

	Boolean approveVendorPayment(Long currentUserId, String status, Long id);

	int getAllVendorPaymentRegisterCountForAccount(String status);

	Boolean addAmountByAccountTeam(VendorPaymentAddDto vendorPaymentAddDto);

	List<Map<String, Object>> getAllVendorPaymentRegisterHistoryById(Long id);

	List<Map<String, Object>> getAllVendorPaymentRegisterForAdmin(int page, int size, String status);

	int getAllVendorPaymentRegisterCountForAdmin(String status);


	VendorPaymentRegister createVendorPaymentRegisterManual(CreateVendorAmountManualDto createVendorAmountManualDto);

}
