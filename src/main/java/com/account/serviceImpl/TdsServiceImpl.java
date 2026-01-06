package com.account.serviceImpl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.account.dto.CreateTdsDto;
import com.account.repository.TdsDetailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.domain.TdsDetail;
import com.account.domain.User;
import com.account.repository.UserRepository;
import com.account.service.TdsService;

@Service
public class TdsServiceImpl implements TdsService{
	
	@Autowired
    TdsDetailRepository tdsDetailRepository;
	
	@Autowired
	UserRepository userRepository;

	@Override
	public TdsDetail createTds(CreateTdsDto createTdsDto) {
		TdsDetail tdsDetail = new TdsDetail();
		tdsDetail.setOrganization(createTdsDto.getOrganization());
		tdsDetail.setPaymentRegisterId(createTdsDto.getPaymentRegisterId());
		tdsDetail.setProjectId(createTdsDto.getProjectId());
		tdsDetail.setTdsAmount(createTdsDto.getTdsAmount());
		tdsDetail.setTdsType("Receivable");
		tdsDetail.setTdsPrecent(createTdsDto.getTdsPrecent());
		tdsDetail.setCreateDate(new Date());
		tdsDetailRepository.save(tdsDetail);
		return tdsDetail;
	}

	@Override
	public List<TdsDetail> getAllTds() {
		List<TdsDetail> tdsList = tdsDetailRepository.findAll();
		return tdsList;
	}

	@Override
	public Map<String, Object> getAllTdsCount() {
		List<TdsDetail> tdsList =tdsDetailRepository.findAll();
		Map<String, Object>map=new HashMap<>();
		double payableTds=0;
		double receivableTds=0;
		for(TdsDetail t:tdsList) {
			if(t.equals("payable")) {
				payableTds=payableTds+t.getTdsAmount();
			}else {
				receivableTds=receivableTds+t.getTdsAmount();
			}
			
		}
		map.put("payableTds", payableTds);
		map.put("receivableTds", receivableTds);

		return map ;
	}

	@Override
	public Boolean updateTdsClaimAmount(Long id, double amount, String document,Long currentUserId) {
		Boolean flag=false;
		TdsDetail tdsDetail = tdsDetailRepository.findById(id).get();
		tdsDetail.setClaimDate(new Date());
		tdsDetail.setDocuments(document);
		tdsDetail.setTdsClaimAmount(amount);
		User user = userRepository.findById(currentUserId).orElse(null);
		tdsDetail.setTdsClaimBy(user);
		tdsDetailRepository.save(tdsDetail);
		flag=true;
		return flag;
	}

}
