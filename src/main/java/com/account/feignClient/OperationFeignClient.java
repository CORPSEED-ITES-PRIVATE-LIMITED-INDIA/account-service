package com.account.feignClient;


import com.account.dto.operationService.*;
import io.swagger.v3.oas.annotations.Parameter;
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


    @GetMapping("/operationService/api/projects/{unbilledNumber}")
    ResponseEntity<OperationProjectResponseDto> getProjectByUnbilledNumber(@PathVariable String unbilledNumber);

    @PostMapping("/operationService/api/projects/payments/unbilled/{unbilledNumber}")
    ResponseEntity<?> addPaymentTransaction(
            @PathVariable @Parameter(description = "Unbilled number of the project")  String unbilledNumber,
            @RequestBody OperationProjectPaymentTransactionDto dto
    );

    @PutMapping("/operationService/api/projects/cancel/{unbilledNumber}")
    ResponseEntity<OperationProjectResponseDto> cancelProjectByUnbilledNumber(@PathVariable String unbilledNumber);


}
