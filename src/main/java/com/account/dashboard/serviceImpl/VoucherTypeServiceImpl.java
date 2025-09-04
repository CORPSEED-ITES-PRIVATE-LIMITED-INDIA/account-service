package com.account.dashboard.serviceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.account.VoucherType;
import com.account.dashboard.repository.VoucherTypeRepo;
import com.account.dashboard.service.VoucherTypeService;

@Service
public class VoucherTypeServiceImpl implements VoucherTypeService{

	@Autowired
	VoucherTypeRepo voucherTypeRepo;
	@Override
	public Boolean createVoucherType(String name) {
		Boolean flag = false;
		VoucherType voucherType  = new VoucherType();
		voucherType.setName(name);
		voucherTypeRepo.save(voucherType);
		flag=true;
		return flag;
	}

	@Override
	public List<VoucherType> getAllVoucherType() {
		List<VoucherType>voucherList=voucherTypeRepo.findAll();
		return voucherList;
	}

	@Override
	public Boolean updateVoucherType(String name, Long id) {
		Boolean flag=false;
		VoucherType voucherType = voucherTypeRepo.findById(id).get();
		voucherType.setName(name);
		voucherTypeRepo.save(voucherType);
		flag=true;
		return flag;
	}

}
