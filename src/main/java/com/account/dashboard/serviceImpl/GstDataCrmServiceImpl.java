package com.account.dashboard.serviceImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

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
	public List<Map<String, Object>> getAllGstDataCrm(int page, int size, String startDate, String endDate) {
		Pageable pageable = PageRequest.of(page, size);
//		 List<GstDataFromCrm> gstData = gstDataFromCrmRepository.findAll(pageable).getContent();
		Page<GstDataFromCrm> gstData =gstDataFromCrmRepository.findAllByDateInBetween(startDate,endDate,pageable);
		List<Map<String, Object>> list = gstData.stream().map(m -> {
		    Map<String, Object> map = new HashMap<>();
		    map.put("id", m.getId());
		    map.put("paymentRegister", m.getPaymentRegister());
		    map.put("gst", m.getGst());
		    map.put("gstAmount", m.getGstAmount());
		    map.put("company", m.getCompany());
		    map.put("type", m.getType());
		    map.put("status", m.getStatus());
		    map.put("createDate", m.getCreateDate());

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
		gstDataFromCrm.setPaymentRegister(paymentRegister);
		gstDataFromCrm.setStatus(addGstDto.getStatus());
		gstDataFromCrm.setCreateDate(new Date());
		gstDataFromCrm.setType(addGstDto.getType());
		gstDataFromCrmRepository.save(gstDataFromCrm);
		flag=true;
		return flag;
	}

	@Override
	public Long getAllGstDataCrmCount(String startDate, String endDate) {
//		List<GstDataFromCrm> gstData = gstDataFromCrmRepository.findAll();
		Long gstData =gstDataFromCrmRepository.findCountByDateInBetween(startDate,endDate);

		return gstData;
	}

	@Override
	public List<Map<String, Object>> getAllGstDataCrmForExport(String startDate, String endDate) {
		List<GstDataFromCrm> gstData =gstDataFromCrmRepository.findAllByDateInBetween(startDate,endDate);

//		List<GstDataFromCrm> gstData = gstDataFromCrmRepository.findAll();
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
		    map.put("createDate", m.getCreateDate());

		    return map;
		}).collect(Collectors.toList());
		return list;
	}

	@Override
	public Boolean updateGstClaimAmount(Long id, double amount, String documents) {
		Boolean flag=false;
		GstDataFromCrm gstDataFrom = gstDataFromCrmRepository.findById(id).get();
		gstDataFrom.setGstClaimAmount(amount);
		gstDataFrom.setDocument(documents);
		gstDataFrom.setCreateDate(new Date());
		gstDataFromCrmRepository.save(gstDataFrom);
		flag=true;
		return flag;
	}

}
