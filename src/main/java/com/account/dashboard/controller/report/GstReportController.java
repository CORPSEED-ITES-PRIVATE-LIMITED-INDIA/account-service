package com.account.dashboard.controller.report;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.GstDetails;
import com.account.dashboard.service.report.GstReportService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class GstReportController {
	
	@Autowired
	GstReportService gstReportService;
	
	@GetMapping(UrlsMapping.GET_ALL_GST_REPORT)
	public List<GstDetails> getAllGstReport(){
		List<GstDetails> res=gstReportService.getAllGstReport();	
		return res;
	}

}
