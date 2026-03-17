package com.account.feignClient;


import com.account.dto.operationService.OperationCompanyRequestDto;
import com.account.dto.operationService.OperationCompanyResponseDto;
import com.account.dto.operationService.OperationProjectRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "OPERATION-SERVICE")
public interface OperationFeignClient {



    @PostMapping("/operationService/api/companies/createCompany")
    ResponseEntity<Void> createCompany(
            @RequestBody OperationCompanyRequestDto dto,
            @RequestParam("companyId") Long companyId
    );


    @GetMapping("/operationService/api/companies/{companyId}")
    ResponseEntity<OperationCompanyResponseDto> getCompanyById(@PathVariable Long companyId);



    @PostMapping("/operationService/api/projects")
    ResponseEntity<?> createProject(@RequestBody OperationProjectRequestDto dto);

}
