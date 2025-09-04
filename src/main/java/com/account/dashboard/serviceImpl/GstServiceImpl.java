package com.account.dashboard.serviceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.GstDetails;
import com.account.dashboard.domain.account.Voucher;
import com.account.dashboard.dto.CreateGstDto;
import com.account.dashboard.repository.GstDetailsRepository;
import com.account.dashboard.repository.VoucherRepository;
import com.account.dashboard.service.GstDetailsService;

@Service
public class GstServiceImpl implements GstDetailsService{
	
	@Autowired
	GstDetailsRepository gstDetailsRepository;
	
	@Autowired
	VoucherRepository voucherRepository;

	@Override
	public Boolean createGst(CreateGstDto createGstDto) {
		Boolean flag=false;
		GstDetails gstDetails = new GstDetails();
		gstDetails.setCgstPercent(0);
		gstDetails.setGstAmount(0);
		gstDetails.setGstType(null);
		gstDetails.setIgstPercent(0);
		gstDetails.setSgstPercent(0);
		gstDetails.setTotalGst(0);
		Voucher voucher = voucherRepository.findById(createGstDto.getVoucherId()).get();
		gstDetails.setVoucher(voucher);
		gstDetailsRepository.save(gstDetails);
		flag=true;
		return flag;
	}

	@Override
	public GstDetails getGstById(Long id) {
		GstDetails gstDetails = gstDetailsRepository.findById(id).get();
		return gstDetails;
	}

	@Override
	public List<GstDetails> getAllGst() {
		 List<GstDetails> gstDetails = gstDetailsRepository.findAll();
//		 for() {
//			 
//		 }
		return gstDetails;
	}

}
