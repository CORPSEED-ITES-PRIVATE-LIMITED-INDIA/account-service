package com.account.feignClient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "LEAD-SERVICE")
public interface LeadFeignClient {

    @PutMapping("/leadService/api/proposals/force-cancel/{userId}/{proposalId}")
    ResponseEntity<String> forceCancelProposal(
            @PathVariable("userId") Long userId,
            @PathVariable("proposalId") Long proposalId,
            @RequestParam(value = "reason", required = false) String reason
    );
}