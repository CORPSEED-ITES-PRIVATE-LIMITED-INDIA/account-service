package com.account.dashboard.serviceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.GstDataFromCrm;
import com.account.dashboard.domain.PaymentRegister;
import com.account.dashboard.dto.AddGstDto;
import com.account.dashboard.repository.GstDataFromCrmRepository;
import com.account.dashboard.repository.PaymentRegisterRepository;
import com.account.dashboard.service.GstDataCrmService;

@Service
public class GstDataCrmServiceImpl implements GstDataCrmService{
	
	@Autowired
	GstDataFromCrmRepository gstDataFromCrmRepository;
	@Autowired
	PaymentRegisterRepository paymentRegisterRepository;

	@Override
	public List<Map<String, Object>> getAllGstDataCrm(int i, int size) {
		List<GstDataFromCrm> gstData = gstDataFromCrmRepository.findAll();
		List<Map<String, Object>> list = gstData.stream().map(m -> {
		    Map<String, Object> map = new HashMap<>();
		    map.put("id", m.getId());
		    map.put("paymentRegister", m.getPaymentRegister());
		    map.put("gst", m.getGst());
		    map.put("gstAmount", m.getGstAmount());
		    map.put("company", m.getCompany());
		    map.put("type", m.getType());
		    map.put("status", m.getStatus());
		    map.put("document", m.getDocument());
		    return map;
		}).collect(Collectors.toList());

		return list;
	}

	@Override
	public Boolean addGstDataCrm(AddGstDto addGstDto) {
		Boolean flag=false;
		GstDataFromCrm gstDataFromCrm=new GstDataFromCrm();
		gstDataFromCrm.setCompany(addGstDto.getCompany());
		gstDataFromCrm.setGst(addGstDto.getGst());
		gstDataFromCrm.setGstAmount(addGstDto.getGstAmount());
		PaymentRegister paymentRegister = paymentRegisterRepository.findById(addGstDto.getPaymentRegisterId()).get();
//		gstDataFromCrm.setPaymentRegister(addGstDto.getPaymentRegisterId());
		gstDataFromCrm.setStatus(addGstDto.getStatus());
		gstDataFromCrm.setType(addGstDto.getType());
		gstDataFromCrmRepository.save(gstDataFromCrm);
		flag=true;
		return flag;
	}

}
