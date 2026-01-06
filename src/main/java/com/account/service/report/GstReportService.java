package com.account.service.report;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.domain.GstDetails;

@Service
public interface GstReportService {

	List<GstDetails> getAllGstReport();

}
