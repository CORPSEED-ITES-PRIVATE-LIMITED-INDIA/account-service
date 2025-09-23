package com.account.dashboard.service.report;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.dashboard.domain.TdsDetail;

@Service
public interface TdsReportService {

	List<TdsDetail> getAllTdsReport();

}
