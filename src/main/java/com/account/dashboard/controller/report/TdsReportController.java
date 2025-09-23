package com.account.dashboard.controller.report;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.account.dashboard.domain.TdsDetail;
import com.account.dashboard.service.report.TdsReportService;
import com.account.dashboard.util.UrlsMapping;

@RestController
public class TdsReportController {
	
	@Autowired
	TdsReportService tdsReportService;
	
	@GetMapping(UrlsMapping.GET_ALL_TDS_REPORT)
	public List<TdsDetail> getAllTdsReport(){
		List<TdsDetail> res=tdsReportService.getAllTdsReport();	
		return res;
		
	}

}
