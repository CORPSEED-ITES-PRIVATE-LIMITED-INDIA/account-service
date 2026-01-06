package com.account.service.report;

import java.util.List;

import org.springframework.stereotype.Service;

import com.account.domain.TdsDetail;

@Service
public interface TdsReportService {

	List<TdsDetail> getAllTdsReport();

}
