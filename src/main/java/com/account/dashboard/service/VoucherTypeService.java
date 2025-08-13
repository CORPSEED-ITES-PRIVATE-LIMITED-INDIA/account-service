package com.account.dashboard.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.VoucherType;

@Service
public interface VoucherTypeService {

	Boolean createVoucherType(String name);

	List<VoucherType> getAllVoucherType();

	Boolean updateVoucherType(String name, Long id);

}
