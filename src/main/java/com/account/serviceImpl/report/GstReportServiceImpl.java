package com.account.serviceImpl.report;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.account.domain.GstDetails;
import com.account.repository.GstDetailsRepository;
import com.account.service.report.GstReportService;

@Service
public class GstReportServiceImpl implements GstReportService{
	

	@Autowired
	GstDetailsRepository gstDetailsRepository;
	@Override
	public List<GstDetails> getAllGstReport() {
		List<GstDetails> gstDetails = gstDetailsRepository.findAll();
		return gstDetails;
	}

}
