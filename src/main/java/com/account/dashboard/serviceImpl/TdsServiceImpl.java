package com.account.dashboard.serviceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.TdsDetail;
import com.account.dashboard.dto.CreateTdsDto;
import com.account.dashboard.repository.TdsDetailRepository;
import com.account.dashboard.service.TdsService;

@Service
public class TdsServiceImpl implements TdsService{
	
	@Autowired
	TdsDetailRepository tdsDetailRepository;

	@Override
	public TdsDetail createTds(CreateTdsDto createTdsDto) {
		TdsDetail tdsDetail = new TdsDetail();
		tdsDetail.setOrganization(createTdsDto.getOrganization());
		tdsDetail.setPaymentRegisterId(createTdsDto.getPaymentRegisterId());
		tdsDetail.setProjectId(createTdsDto.getProjectId());
		tdsDetail.setTdsAmount(createTdsDto.getTdsAmount());
		tdsDetail.setTdsPrecent(createTdsDto.getTdsPrecent());
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

}
