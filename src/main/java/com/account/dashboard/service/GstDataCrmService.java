package com.account.dashboard.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public interface GstDataCrmService {

	List<Map<String, Object>> getAllGstDataCrm(int i, int size);

}
