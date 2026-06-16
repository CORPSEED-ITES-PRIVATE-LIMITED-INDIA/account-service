package com.account.config;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;


@Service
@FeignClient(name="LEAD-SERVICE", url="http://localhost:9001")
public interface LeadFeignClient {

	
	@PostMapping("/leadService/api/v1/users/getAllUserForAccount")
	List<Map<String,Object>> getAllUserForAccount();

	@PutMapping("/leadService/api/proposals/force-cancel/{userId}/{proposalId}")
	ResponseEntity<String> forceCancelProposal(
			@PathVariable("userId") Long userId,
			@PathVariable("proposalId") Long proposalId,
			@RequestParam(value = "reason", required = false) String reason
	);
	
}
