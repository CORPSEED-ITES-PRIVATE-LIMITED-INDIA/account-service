package com.account.feignClient;


import com.account.dto.operationService.OperationCompanyRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "OPERATION-SERVICE")
public interface OperationFeignClient {



    @PostMapping("/api/companies/createCompany")
    ResponseEntity<Void> createCompany(
            @RequestBody OperationCompanyRequestDto dto,
            @RequestParam("companyId") Long companyId
    );



}
