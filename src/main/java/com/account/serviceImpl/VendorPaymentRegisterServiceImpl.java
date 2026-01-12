package com.account.serviceImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.account.domain.FileData;
import com.account.domain.ProductEstimate;
import com.account.domain.TdsDetail;
import com.account.domain.User;
import com.account.domain.VendorPaymentHistory;
import com.account.domain.VendorPaymentRegister;
import com.account.dto.AddGstDto;
import com.account.dto.CreateVendorAmountDto;
import com.account.dto.CreateVendorAmountManualDto;
import com.account.dto.CreateVendorSubDto;
import com.account.dto.VendorPaymentAddDto;
import com.account.repository.FileDataRepository;
import com.account.repository.ProductEstimateRepository;
import com.account.repository.TdsDetailRepository;
import com.account.repository.UserRepository;
import com.account.repository.VendorPaymentHistoryRepository;
import com.account.repository.VendorPaymentRegisterRepo;
import com.account.service.VendorPaymentRegisterServcie;

@Service
public class VendorPaymentRegisterServiceImpl implements VendorPaymentRegisterServcie {

	
	@Autowired
	VendorPaymentRegisterRepo vendorPaymentRegisterRepo;
	
	@Autowired
	UserRepository userRepository;
	
	@Autowired
	GstDataCrmServiceImpl GstDataCrmService;
	
	@Autowired
	FileDataRepository fileDataRepository;
	
	@Autowired
	ProductEstimateRepository productEstimateRepository;
	@Autowired
	VendorPaymentHistoryRepository vendorPaymentHistoryRepository;
	
	@Autowired
	TdsDetailRepository tdsDetailRepository;


	@Override
	public VendorPaymentRegister createVendorPaymentRegister(CreateVendorAmountDto createVendorAmountDto) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegister(int page, int size) {
		return null;
	}

	@Override
	public int getAllVendorPaymentRegisterCount() {
		return 0;
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegisterForAccount(int page, int size, String status) {
		return null;
	}

	@Override
	public Boolean approveVendorPayment(Long currentUserId, String status, Long id) {
		return null;
	}

	@Override
	public int getAllVendorPaymentRegisterCountForAccount(String status) {
		return 0;
	}

	@Override
	public Boolean addAmountByAccountTeam(VendorPaymentAddDto vendorPaymentAddDto) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegisterHistoryById(Long id) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getAllVendorPaymentRegisterForAdmin(int page, int size, String status) {
		return null;
	}

	@Override
	public int getAllVendorPaymentRegisterCountForAdmin(String status) {
		return 0;
	}

	@Override
	public VendorPaymentRegister createVendorPaymentRegisterManual(CreateVendorAmountManualDto createVendorAmountManualDto) {
		return null;
	}
}
