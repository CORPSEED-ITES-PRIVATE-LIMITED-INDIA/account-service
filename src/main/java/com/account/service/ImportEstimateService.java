package com.account.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.account.domain.PaymentRegister;
import com.account.domain.User;

@Service
public interface ImportEstimateService {

	List<Map<String, Object>> importEstimateData(String s3Url);

	List<User>  importUserData(String s3Url);

	List<PaymentRegister> importPaymentData(String s3Url);
	
	

}
