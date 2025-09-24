package com.account.dashboard.serviceImpl.report;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.account.dashboard.domain.GstDetails;
import com.account.dashboard.repository.GstDetailsRepository;
import com.account.dashboard.service.report.GstReportService;

public class GstReportServiceImpl implements GstReportService{
	

	@Autowired
	GstDetailsRepository gstDetailsRepository;
	@Override
	public List<GstDetails> getAllGstReport() {
		List<GstDetails> gstDetails = gstDetailsRepository.findAll();
		return gstDetails;
	}

}
