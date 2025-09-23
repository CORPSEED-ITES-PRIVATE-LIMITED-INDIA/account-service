package com.account.dashboard.serviceImpl.report;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.dashboard.domain.TdsDetail;
import com.account.dashboard.repository.TdsDetailRepository;
import com.account.dashboard.service.report.TdsReportService;

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
