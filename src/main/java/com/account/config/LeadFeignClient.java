package com.account.config;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@Service
@FeignClient(name="LEAD-SERVICE", url="http://localhost:9001")
public interface LeadFeignClient {

	
	@PostMapping("/leadService/api/v1/users/getAllUserForAccount")
	public List<Map<String,Object>> getAllUserForAccount();

	
}
