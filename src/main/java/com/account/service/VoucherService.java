package com.account.service;

import java.util.List;
import java.util.Map;

import com.account.dto.CreateVoucherDto;
import org.springframework.stereotype.Service;

import com.account.domain.account.Voucher;

@Service
public interface VoucherService {

	Boolean createVoucher(CreateVoucherDto createVoucherDto);

	List<Voucher> getAllVoucher();

	Map<String, Object> getVoucherAmount();

	Map<String, Object> getAllVoucherByLedgerId(Long ledgerId);

	Map<String, Object> getAllVoucherInBetween(String startDate, String endDate);

	Map<String, Object> getAllVoucherByGroup(Long id);

	List<Map<String, Object>> getAllVoucherV2();

	Map<String, Object> getVoucheById(Long id);

	Boolean createVoucherV2(CreateVoucherDto createVoucherDto);

	List<Map<String, Object>> getAllVoucherForExport();

	Boolean deleteVoucherById(Long id);

}
