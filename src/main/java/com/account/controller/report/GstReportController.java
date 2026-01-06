package com.account.controller.report;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.account.domain.GstDetails;
import com.account.service.report.GstReportService;
import com.account.util.UrlsMapping;

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
