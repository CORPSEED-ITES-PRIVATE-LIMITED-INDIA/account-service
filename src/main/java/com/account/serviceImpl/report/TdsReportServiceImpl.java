package com.account.serviceImpl.report;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.domain.TdsDetail;
import com.account.repository.TdsDetailRepository;
import com.account.service.report.TdsReportService;

@Service
public class TdsReportServiceImpl implements TdsReportService {

	@Autowired
	TdsDetailRepository tdsDetailRepository;
	
	@Override
	public List<TdsDetail> getAllTdsReport() {
		List<TdsDetail> tdsData=tdsDetailRepository.findAll();
		return tdsData;
	}

}
