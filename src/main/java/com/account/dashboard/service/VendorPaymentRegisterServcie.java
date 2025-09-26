package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.domain.VendorPaymentRegister;
import com.account.dashboard.dto.CreateAmountDto;
import com.account.dashboard.dto.CreateVendorAmountDto;

@Service
public interface VendorPaymentRegisterServcie {

	VendorPaymentRegister createVendorPaymentRegister(CreateVendorAmountDto createVendorAmountDto);

	List<Map<String, Object>> getAllVendorPaymentRegister(int page, int size);

	int getAllVendorPaymentRegisterCount();

	List<Map<String, Object>> getAllVendorPaymentRegisterForAccount(int page,int size,String status);

	Boolean approveVendorPayment(Long currentUserId, String status, Long id);

	int getAllVendorPaymentRegisterCountForAccount(String status);

}
