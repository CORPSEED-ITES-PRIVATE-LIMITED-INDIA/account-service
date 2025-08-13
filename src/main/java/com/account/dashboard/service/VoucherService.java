package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.Ledger;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.dto.CreateVoucherDto;

@Service
public interface VoucherService {

	Boolean createVoucher(CreateVoucherDto createVoucherDto);

	List<Voucher> getAllVoucher();

	Map<String, Object> getVoucherAmount();

	Map<String, Object> getAllVoucherByLedgerId(Long ledgerId);

	Map<String, Object> getAllVoucherInBetween(String startDate, String endDate);

	Map<String, Object> getAllVoucherByGroup(Long id);

	List<Map<String, Object>> getAllVoucherV2();

}
