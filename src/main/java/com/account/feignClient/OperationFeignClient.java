package com.account.feignClient;


import com.account.dto.operationService.*;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "OPERATION-SERVICE")
public interface OperationFeignClient {


    @PostMapping("/operationService/api/companies/createCompany")
    ResponseEntity<Void> createCompany(
            @RequestBody OperationCompanyRequestDto dto,
            @RequestParam("companyId") Long companyId
    );

    @PostMapping("/operationService/api/companies/syncCompany")
    ResponseEntity<OperationCompanyResponseDto> syncCompany(
            @RequestBody OperationCompanyRequestDto dto,
            @RequestParam("companyId") Long companyId
    );

    @GetMapping("/operationService/api/companies/{companyId}")
    ResponseEntity<OperationCompanyResponseDto> getCompanyById(@PathVariable Long companyId);

    @PostMapping("/operationService/api/projects")
    ResponseEntity<?> createProject(@RequestBody OperationProjectRequestDto operationProjectRequestDto);


    @GetMapping("/operationService/api/projects/{unbilledNumber}")
    ResponseEntity<OperationProjectResponseDto> getProjectByUnbilledNumber(@PathVariable String unbilledNumber);

    @PostMapping("/operationService/api/projects/payments/unbilled/{unbilledNumber}")
    ResponseEntity<?> addPaymentTransaction(
            @PathVariable @Parameter(description = "Unbilled number of the project")  String unbilledNumber,
            @RequestBody OperationProjectPaymentTransactionDto dto
    );

    @PutMapping("/operationService/api/projects/cancel/{userId}/{unbilledNumber}")
    ResponseEntity<OperationProjectResponseDto> cancelProjectByUnbilledNumber(
            @PathVariable Long userId,
            @PathVariable String unbilledNumber
    );

    @PutMapping("/operationService/api/projects/{projectId}/activities/approveExpense/{userId}/{expenseId}")
    ResponseEntity<?> approveExpense(
            @PathVariable("projectId") Long projectId,
            @PathVariable("userId") Long userId,
            @PathVariable("expenseId") Long expenseId,
            @RequestParam String status
    );

    @GetMapping("/operationService/api/projects/{projectId}/activities/type/{type}")
    ResponseEntity<Page<OperationProjectActivityResponseDto>> getActivitiesByType(
            @PathVariable Long projectId,
            @PathVariable ActivityType type,
            Pageable pageable
    );

    @GetMapping("/operationService/api/purchase-orders")
    ResponseEntity<?> getProcurementPurchaseOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @PutMapping("/operationService/api/purchase-orders/{purchaseOrderId}/approve/{userId}")
    ResponseEntity<?> approveProcurementPurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @PathVariable Long userId,
            @RequestBody(required = false) ProcurementPurchaseOrderActionRequestDto request
    );

    @PutMapping("/operationService/api/purchase-orders/{purchaseOrderId}/reject/{userId}")
    ResponseEntity<?> rejectProcurementPurchaseOrder(
            @PathVariable Long purchaseOrderId,
            @PathVariable Long userId,
            @RequestBody ProcurementPurchaseOrderActionRequestDto request
    );


    // ============================================================
    // PROCUREMENT PAYMENT REQUEST APIs
    // ============================================================

    @GetMapping("/operationService/api/procurement-payment-requests")
    ResponseEntity<?> getProcurementPaymentRequests(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    );

    @PutMapping("/operationService/api/procurement-payment-requests/{paymentRequestId}/approve/{userId}")
    ResponseEntity<?> approveProcurementPaymentRequest(
            @PathVariable Long paymentRequestId,
            @PathVariable Long userId,
            @RequestBody(required = false) ProcurementPaymentActionRequestDto request
    );

    @PutMapping("/operationService/api/procurement-payment-requests/{paymentRequestId}/reject/{userId}")
    ResponseEntity<?> rejectProcurementPaymentRequest(
            @PathVariable Long paymentRequestId,
            @PathVariable Long userId,
            @RequestBody ProcurementPaymentActionRequestDto request
    );

    @PutMapping("/operationService/api/procurement-payment-requests/{paymentRequestId}/release-payment/{userId}")
    ResponseEntity<?> releaseProcurementPayment(
            @PathVariable Long paymentRequestId,
            @PathVariable Long userId,
            @RequestBody(required = false) ProcurementPaymentActionRequestDto request
    );


    @GetMapping("/operationService/api/v1/projects/by-estimate/{estimateId}")
    ResponseEntity<AdvanceInvoiceOperationProjectResponseDto> getProjectByEstimateId(
            @PathVariable("estimateId") Long estimateId
    );

    @PostMapping("/operationService/api/v1/projects/from-advance-invoice")
    ResponseEntity<AdvanceInvoiceOperationProjectResponseDto>
    createOrSyncProjectFromAdvanceInvoice(
            @RequestBody AdvanceInvoiceOperationProjectRequestDto request
    );



}
