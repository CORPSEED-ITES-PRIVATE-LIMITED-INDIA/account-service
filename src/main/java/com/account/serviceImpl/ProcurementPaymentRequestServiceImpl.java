package com.account.serviceImpl;

import com.account.domain.Organization;
import com.account.dto.ProcurementPaymentRequestResponseDto;
import com.account.dto.operationService.ProcurementPaymentActionRequestDto;
import com.account.dto.procurement.OperationApiResponseDto;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.OrganizationRepository;
import com.account.dto.PagedResponse;
import com.account.service.ProcurementPaymentRequestService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcurementPaymentRequestServiceImpl implements ProcurementPaymentRequestService {

    private final OperationFeignClient operationFeignClient;
    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional(readOnly = true)
    public OperationApiResponseDto<?> getProcurementPaymentRequests(String status, int page, int size) {
        try {
            ResponseEntity<PagedResponse<ProcurementPaymentRequestResponseDto>> response =
                    operationFeignClient.getProcurementPaymentRequests(status, page, size);

            PagedResponse<ProcurementPaymentRequestResponseDto> pagedResponse = response.getBody();

            Organization org = organizationRepository.findTopOrganization().orElse(null);

            if (pagedResponse != null && pagedResponse.getContent() != null) {
                pagedResponse.getContent().forEach(dto -> enrichWithOrganizationData(dto, org));
            }

            return OperationApiResponseDto.builder()
                    .success(true)
                    .message("Procurement payment requests fetched successfully")
                    .statusCode(response.getStatusCode().value())
                    .data(pagedResponse)
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (FeignException e) {
            return buildFeignErrorResponse(e);
        } catch (Exception e) {
            return buildInternalErrorResponse(e);
        }
    }

    private void enrichWithOrganizationData(ProcurementPaymentRequestResponseDto dto, Organization org) {
        if (org == null) return;
        dto.setOrganizationName(org.getName());
        dto.setOrganizationAddressLine1(org.getAddressLine1());
        dto.setOrganizationAddressLine2(org.getAddressLine2());
        dto.setOrganizationCity(org.getCity());
        dto.setOrganizationState(org.getState());
        dto.setOrganizationCountry(org.getCountry());
        dto.setOrganizationPinCode(org.getPinCode());
        dto.setOrganizationGstNo(org.getGstNo());
        dto.setOrganizationPanNo(org.getPanNo());
        dto.setOrganizationCinNumber(org.getCinNumber());
        dto.setOrganizationEmail(org.getEmail());
        dto.setOrganizationPhone(org.getPhone());
        dto.setOrganizationWebsite(org.getWebsite());
        dto.setOrganizationLogoUrl(org.getLogoUrl());
        dto.setOrganizationBankAccountPresent(org.getAccountNo() != null);
        dto.setOrganizationAccountHolderName(org.getAccountHolderName());
        dto.setOrganizationAccountNumber(org.getAccountNo());
        dto.setOrganizationIfscCode(org.getIfscCode());
        dto.setOrganizationSwiftCode(org.getSwiftCode());
        dto.setOrganizationBankName(org.getBankName());
        dto.setOrganizationBankBranch(org.getBranch());
        dto.setOrganizationUpiId(org.getUpiId());
        dto.setOrganizationPaymentPageLink(org.getPaymentPageLink());
    }

    @Override
    @Transactional
    public OperationApiResponseDto<?> approveProcurementPaymentRequest(
            Long paymentRequestId,
            Long userId,
            String comment
    ) {
        validatePaymentRequestAndUser(paymentRequestId, userId);

        try {
            ProcurementPaymentActionRequestDto request =
                    ProcurementPaymentActionRequestDto.builder()
                            .comment(comment)
                            .build();

            ResponseEntity<?> response = operationFeignClient.approveProcurementPaymentRequest(
                    paymentRequestId,
                    userId,
                    request
            );

            return OperationApiResponseDto.builder()
                    .success(true)
                    .message("Procurement payment request approved successfully")
                    .statusCode(response.getStatusCode().value())
                    .data(response.getBody())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (FeignException e) {
            return buildFeignErrorResponse(e);
        } catch (Exception e) {
            return buildInternalErrorResponse(e);
        }
    }

    @Override
    @Transactional
    public OperationApiResponseDto<?> rejectProcurementPaymentRequest(
            Long paymentRequestId,
            Long userId,
            String reason
    ) {
        validatePaymentRequestAndUser(paymentRequestId, userId);

        if (reason == null || reason.trim().isEmpty()) {
            throw new ValidationException(
                    "Rejection reason is required",
                    "ERR_REJECTION_REASON_REQUIRED",
                    "reason"
            );
        }

        try {
            ProcurementPaymentActionRequestDto request =
                    ProcurementPaymentActionRequestDto.builder()
                            .reason(reason.trim())
                            .build();

            ResponseEntity<?> response = operationFeignClient.rejectProcurementPaymentRequest(
                    paymentRequestId,
                    userId,
                    request
            );

            return OperationApiResponseDto.builder()
                    .success(true)
                    .message("Procurement payment request rejected successfully")
                    .statusCode(response.getStatusCode().value())
                    .data(response.getBody())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (FeignException e) {
            return buildFeignErrorResponse(e);
        } catch (Exception e) {
            return buildInternalErrorResponse(e);
        }
    }

    @Override
    @Transactional
    public OperationApiResponseDto<?> releaseProcurementPayment(
            Long paymentRequestId,
            Long userId,
            ProcurementPaymentActionRequestDto request
    ) {
        validatePaymentRequestAndUser(paymentRequestId, userId);

        if (request == null) {
            throw new ValidationException(
                    "Payment release request is required",
                    "ERR_PAYMENT_RELEASE_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (request.getInvoiceNumber() == null || request.getInvoiceNumber().trim().isEmpty()) {
            throw new ValidationException(
                    "Invoice number is required for payment release",
                    "ERR_INVOICE_NUMBER_REQUIRED",
                    "invoiceNumber"
            );
        }

        if (request.getInvoiceDate() == null) {
            throw new ValidationException(
                    "Invoice date is required for payment release",
                    "ERR_INVOICE_DATE_REQUIRED",
                    "invoiceDate"
            );
        }

        try {
            ResponseEntity<?> response = operationFeignClient.releaseProcurementPayment(
                    paymentRequestId,
                    userId,
                    request
            );

            return OperationApiResponseDto.builder()
                    .success(true)
                    .message("Procurement payment released successfully")
                    .statusCode(response.getStatusCode().value())
                    .data(response.getBody())
                    .timestamp(LocalDateTime.now())
                    .build();

        } catch (FeignException e) {
            return buildFeignErrorResponse(e);
        } catch (Exception e) {
            return buildInternalErrorResponse(e);
        }
    }

    private void validatePaymentRequestAndUser(Long paymentRequestId, Long userId) {
        if (paymentRequestId == null) {
            throw new ValidationException(
                    "Payment request id is required",
                    "ERR_PAYMENT_REQUEST_ID_REQUIRED",
                    "paymentRequestId"
            );
        }

        if (userId == null) {
            throw new ValidationException(
                    "User id is required",
                    "ERR_USER_ID_REQUIRED",
                    "userId"
            );
        }
    }

    private OperationApiResponseDto<?> buildFeignErrorResponse(FeignException e) {
        String errorBody = e.contentUTF8();

        String message = errorBody != null && !errorBody.trim().isEmpty()
                ? errorBody
                : "Operation service error occurred";

        return OperationApiResponseDto.builder()
                .success(false)
                .message(message)
                .statusCode(e.status())
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private OperationApiResponseDto<?> buildInternalErrorResponse(Exception e) {
        return OperationApiResponseDto.builder()
                .success(false)
                .message(e.getMessage() != null ? e.getMessage() : "Internal server error")
                .statusCode(500)
                .data(null)
                .timestamp(LocalDateTime.now())
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getApprovedOrReleasedPayments(
            int page,
            int size
    ) {

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0
                ? 10
                : Math.min(size, 100);

        try {

            /*
             * =========================================================
             * FETCH APPROVED PAYMENTS
             * =========================================================
             *
             * Accounts has approved these PRs,
             * but payment has not yet been released.
             */
            ResponseEntity<PagedResponse<ProcurementPaymentRequestResponseDto>>
                    approvedResponse =
                    operationFeignClient.getProcurementPaymentRequests(
                            "APPROVED",
                            safePage,
                            safeSize
                    );

            PagedResponse<ProcurementPaymentRequestResponseDto>
                    approvedPayments =
                    approvedResponse.getBody();


            /*
             * =========================================================
             * FETCH PAYMENT RELEASED
             * =========================================================
             *
             * These PRs have completed the payment release flow.
             */
            ResponseEntity<PagedResponse<ProcurementPaymentRequestResponseDto>>
                    releasedResponse =
                    operationFeignClient.getProcurementPaymentRequests(
                            "PAYMENT_RELEASED",
                            safePage,
                            safeSize
                    );

            PagedResponse<ProcurementPaymentRequestResponseDto>
                    releasedPayments =
                    releasedResponse.getBody();


            /*
             * =========================================================
             * ORGANIZATION DETAILS
             * =========================================================
             */
            Organization organization =
                    organizationRepository
                            .findTopOrganization()
                            .orElse(null);


            /*
             * Enrich APPROVED payments.
             */
            if (approvedPayments != null
                    && approvedPayments.getContent() != null) {

                approvedPayments
                        .getContent()
                        .forEach(dto ->
                                enrichWithOrganizationData(
                                        dto,
                                        organization
                                )
                        );
            }


            /*
             * Enrich PAYMENT_RELEASED payments.
             */
            if (releasedPayments != null
                    && releasedPayments.getContent() != null) {

                releasedPayments
                        .getContent()
                        .forEach(dto ->
                                enrichWithOrganizationData(
                                        dto,
                                        organization
                                )
                        );
            }


            /*
             * =========================================================
             * DATA MAP
             * =========================================================
             */
            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "approvedPayments",
                    approvedPayments
            );

            data.put(
                    "releasedPayments",
                    releasedPayments
            );


            /*
             * =========================================================
             * FINAL RESPONSE
             * =========================================================
             */
            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "success",
                    true
            );

            response.put(
                    "message",
                    "Approved and released procurement payments fetched successfully"
            );

            response.put(
                    "statusCode",
                    200
            );

            response.put(
                    "data",
                    data
            );

            response.put(
                    "timestamp",
                    LocalDateTime.now()
            );

            return response;

        } catch (FeignException e) {

            Map<String, Object> response =
                    new LinkedHashMap<>();

            String errorBody =
                    e.contentUTF8();

            response.put(
                    "success",
                    false
            );

            response.put(
                    "message",
                    errorBody != null
                            && !errorBody.trim().isEmpty()
                            ? errorBody
                            : "Unable to fetch procurement payments from Operation Service"
            );

            response.put(
                    "statusCode",
                    e.status() > 0
                            ? e.status()
                            : 500
            );

            response.put(
                    "data",
                    null
            );

            response.put(
                    "timestamp",
                    LocalDateTime.now()
            );

            return response;

        } catch (Exception e) {

            Map<String, Object> response =
                    new LinkedHashMap<>();

            response.put(
                    "success",
                    false
            );

            response.put(
                    "message",
                    e.getMessage() != null
                            ? e.getMessage()
                            : "Internal server error"
            );

            response.put(
                    "statusCode",
                    500
            );

            response.put(
                    "data",
                    null
            );

            response.put(
                    "timestamp",
                    LocalDateTime.now()
            );

            return response;
        }
    }


}