package com.account.dashboard.service.report;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.GstDetails;

@Service
public interface GstReportService {

	List<GstDetails> getAllGstReport();

}
