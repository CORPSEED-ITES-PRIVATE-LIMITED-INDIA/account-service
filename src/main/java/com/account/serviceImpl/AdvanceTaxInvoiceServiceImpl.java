package com.account.serviceImpl;

import com.account.domain.Organization;
import com.account.domain.PaymentReceipt;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.domain.invoice.*;
import com.account.domain.ledger.*;
import com.account.domain.status.InvoiceStatus;
import com.account.domain.status.PaymentStatus;
import com.account.domain.status.UnbilledStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.invoice.*;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.operationService.OperationProjectResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.*;
import com.account.service.AdvanceTaxInvoiceService;
import com.account.service.InvoiceService;
import com.account.service.ledger.AccountingVoucherService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.account.dto.invoice.ConfirmAdvanceInvoiceResponseDto;
import com.account.dto.invoice.ConfirmInvoiceEInvoiceRequestDto;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdvanceTaxInvoiceServiceImpl implements AdvanceTaxInvoiceService {

    private static final BigDecimal MINIMUM_REQUEST_PERCENTAGE =
            new BigDecimal("0.25");

    private final AdvanceTaxInvoiceRequestRepository
            advanceTaxInvoiceRequestRepository;

    private final UserRepository userRepository;

    private final UnbilledInvoiceRepository
            unbilledInvoiceRepository;

    private final InvoiceService invoiceService;

    private final OrganizationRepository organizationRepository;

    private final OperationFeignClient operationFeignClient;

    private final InvoiceRepository invoiceRepository;
    private final AccountingVoucherService accountingVoucherService;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private static final Logger log =
            LoggerFactory.getLogger(AdvanceTaxInvoiceServiceImpl.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public AdvanceTaxInvoiceResponseDto createRequest(
            AdvanceTaxInvoiceCreateRequestDto requestDto
    ) {
        // =====================================================
        // 1. VALIDATE REQUEST AND LOCK ESTIMATE
        // =====================================================
        validateCreateRequest(requestDto);

        Estimate estimate =
                findEstimateForUpdate(
                        requestDto.getEstimateId()
                );

        validateEstimateForAdvanceInvoice(estimate);

        User requestedBy =
                getActiveUser(
                        requestDto.getRequestedByUserId(),
                        "requestedByUserId"
                );

        // =====================================================
        // 2. RESOLVE NORMAL FLOW OR COMPLETED PO CONVERSION
        // =====================================================
        /*
         * Normal Advance Tax Invoice:
         *     existingUnbilled == null
         *
         * Completed zero-value PURCHASE_ORDER conversion:
         *     existingUnbilled != null
         *     the same approved PO Unbilled record must remain
         *     and will be linked to the generated Invoice.
         */
        UnbilledInvoice existingUnbilled =
                unbilledInvoiceRepository
                        .findByEstimateAndIsCancelledFalse(
                                estimate
                        )
                        .orElse(null);

        boolean purchaseOrderConversion =
                existingUnbilled != null;

        OperationProjectResponseDto existingPoProject = null;

        if (purchaseOrderConversion) {
            existingPoProject =
                    validatePurchaseOrderConversionEligibility(
                            estimate,
                            existingUnbilled
                    );
        }

        // =====================================================
        // 3. PREVENT DUPLICATE PENDING REQUEST
        // =====================================================
        boolean pendingRequestExists =
                advanceTaxInvoiceRequestRepository
                        .existsByEstimateAndStatus(
                                estimate,
                                AdvanceTaxInvoiceRequestStatus.PENDING
                        );

        if (pendingRequestExists) {
            throw new ValidationException(
                    "A pending Advance Tax Invoice request already exists "
                            + "for Estimate "
                            + estimate.getEstimateNumber(),
                    "ERR_ADVANCE_INVOICE_REQUEST_ALREADY_PENDING",
                    "estimateId"
            );
        }

        // =====================================================
        // 4. CALCULATE REMAINING INVOICEABLE AMOUNT
        // =====================================================
        BigDecimal estimateTotal =
                money(estimate.getGrandTotal());

        BigDecimal alreadyInvoicedAmount =
                getAlreadyInvoicedAdvanceAmount(
                        estimate.getId()
                );

        BigDecimal pendingRequestedAmount =
                money(
                        advanceTaxInvoiceRequestRepository
                                .sumAmountByEstimateAndStatus(
                                        estimate,
                                        AdvanceTaxInvoiceRequestStatus.PENDING
                                )
                );

        BigDecimal remainingInvoiceableAmount =
                estimateTotal
                        .subtract(alreadyInvoicedAmount)
                        .subtract(pendingRequestedAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal requestedAmount;

        // =====================================================
        // 5. RESOLVE REQUEST AMOUNT
        // =====================================================
        if (purchaseOrderConversion) {
            /*
             * For the completed zero-value PURCHASE_ORDER flow,
             * always use the complete remaining invoiceable amount.
             *
             * Any requestedAmount received from an older frontend,
             * Postman request, or another client is intentionally ignored.
             */
            requestedAmount =
                    remainingInvoiceableAmount;

            if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "No remaining invoiceable amount is available for "
                                + "the PURCHASE_ORDER conversion.",
                        "ERR_NO_REMAINING_INVOICEABLE_AMOUNT",
                        "estimateId"
                );
            }

            log.info(
                    "Completed PURCHASE_ORDER Advance Tax Invoice request allowed "
                            + "| estimateId={} | estimateNumber={} "
                            + "| unbilledId={} | unbilledNumber={} "
                            + "| projectNo={} | frontendRequestedAmount={} "
                            + "| systemRequestedAmount={}",
                    estimate.getId(),
                    estimate.getEstimateNumber(),
                    existingUnbilled.getId(),
                    existingUnbilled.getUnbilledNumber(),
                    existingPoProject != null
                            ? existingPoProject.getProjectNo()
                            : null,
                    requestDto.getRequestedAmount(),
                    requestedAmount
            );

        } else {
            if (requestDto.getRequestedAmount() == null) {
                throw new ValidationException(
                        "requestedAmount is required for a normal "
                                + "Advance Tax Invoice request.",
                        "ERR_REQUESTED_AMOUNT_REQUIRED",
                        "requestedAmount"
                );
            }

            requestedAmount =
                    money(requestDto.getRequestedAmount());

            validateRequestedAmount(
                    requestedAmount,
                    estimateTotal,
                    remainingInvoiceableAmount,
                    "requestedAmount"
            );
        }

        // =====================================================
        // 6. CREATE REQUEST
        // =====================================================
        AdvanceTaxInvoiceRequest request =
                new AdvanceTaxInvoiceRequest();

        request.setEstimate(estimate);
        request.setRequestedAmount(requestedAmount);
        request.setApprovedAmount(null);
        request.setStatus(
                AdvanceTaxInvoiceRequestStatus.PENDING
        );
        request.setRequestRemarks(
                clean(requestDto.getRequestRemarks())
        );
        request.setReviewRemarks(null);
        request.setRequestedBy(requestedBy);
        request.setReviewedBy(null);
        request.setReviewedAt(null);

        AdvanceTaxInvoiceRequest saved =
                advanceTaxInvoiceRequestRepository.save(
                        request
                );

        String message =
                purchaseOrderConversion
                        ? "Advance Tax Invoice request created for completed "
                        + "PURCHASE_ORDER using the complete remaining "
                        + "Estimate amount of ₹"
                        + requestedAmount
                        + ". Existing Unbilled Invoice "
                        + existingUnbilled.getUnbilledNumber()
                        + " will remain linked with the generated Invoice."
                        : "Advance Tax Invoice request created successfully "
                        + "and is awaiting Accounts approval.";

        return mapToResponse(
                saved,
                message
        );
    }

    @Override
    @Transactional
    public AdvanceTaxInvoiceResponseDto approveRequest(
            Long requestId,
            AdvanceTaxInvoiceApprovalRequestDto requestDto
    ) {
        // =====================================================
        // 1. BASIC VALIDATION
        // =====================================================
        if (requestId == null || requestId <= 0) {
            throw new ValidationException(
                    "Valid requestId is required",
                    "ERR_ADVANCE_REQUEST_ID_REQUIRED",
                    "requestId"
            );
        }

        if (requestDto == null) {
            throw new ValidationException(
                    "Approval request is required",
                    "ERR_ADVANCE_APPROVAL_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (requestDto.getApproverUserId() == null
                || requestDto.getApproverUserId() <= 0) {

            throw new ValidationException(
                    "approverUserId is required",
                    "ERR_APPROVER_USER_REQUIRED",
                    "approverUserId"
            );
        }

        // =====================================================
        // 2. FETCH AND LOCK REQUEST
        // =====================================================
        AdvanceTaxInvoiceRequest request =
                advanceTaxInvoiceRequestRepository
                        .findByIdForUpdate(requestId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Advance Tax Invoice request "
                                                + "not found with ID: "
                                                + requestId,
                                        "ADVANCE_TAX_INVOICE_REQUEST_NOT_FOUND",
                                        "AdvanceTaxInvoiceRequest",
                                        requestId
                                )
                        );

        if (request.getStatus()
                != AdvanceTaxInvoiceRequestStatus.PENDING) {

            throw new ValidationException(
                    "Only PENDING Advance Tax Invoice requests "
                            + "can be approved. Current status: "
                            + request.getStatus(),
                    "ERR_ADVANCE_REQUEST_NOT_PENDING",
                    "requestId"
            );
        }

        // =====================================================
        // 3. AUTHORIZE APPROVER
        // =====================================================
        User approver =
                getActiveUser(
                        requestDto.getApproverUserId(),
                        "approverUserId"
                );

        validateAccountsOrAdmin(approver);

        if (request.getEstimate() == null
                || request.getEstimate().getId() == null) {

            throw new ValidationException(
                    "Estimate is missing from the Advance Tax Invoice request",
                    "ERR_ESTIMATE_NOT_LINKED_WITH_ADVANCE_REQUEST",
                    "requestId"
            );
        }

        Estimate estimate =
                findEstimateForUpdate(
                        request.getEstimate().getId()
                );

        validateEstimateForAdvanceInvoice(estimate);

        // =====================================================
        // 4. REVALIDATE COMPLETED PO CONVERSION
        // =====================================================
        UnbilledInvoice existingUnbilled =
                unbilledInvoiceRepository
                        .findByEstimateAndIsCancelledFalse(
                                estimate
                        )
                        .orElse(null);

        boolean purchaseOrderConversion =
                existingUnbilled != null;

        OperationProjectResponseDto existingPoProject = null;

        if (purchaseOrderConversion) {
            existingPoProject =
                    validatePurchaseOrderConversionEligibility(
                            estimate,
                            existingUnbilled
                    );
        }

        // =====================================================
        // 5. RESOLVE APPROVED AMOUNT
        // =====================================================
        BigDecimal requestedAmount =
                money(request.getRequestedAmount());

        BigDecimal approvedAmount;

        if (purchaseOrderConversion) {
            if (requestDto.getApprovedAmount() != null
                    && money(requestDto.getApprovedAmount())
                    .compareTo(requestedAmount) != 0) {

                throw new ValidationException(
                        "A completed zero-value PURCHASE_ORDER conversion "
                                + "must be approved for the complete requested "
                                + "amount of ₹"
                                + requestedAmount
                                + ". Partial approval is not allowed.",
                        "ERR_PO_CONVERSION_PARTIAL_APPROVAL_NOT_ALLOWED",
                        "approvedAmount"
                );
            }

            approvedAmount =
                    requestedAmount;

        } else {
            if (requestDto.getApprovedAmount() == null) {
                throw new ValidationException(
                        "approvedAmount is required",
                        "ERR_APPROVED_AMOUNT_REQUIRED",
                        "approvedAmount"
                );
            }

            approvedAmount =
                    money(requestDto.getApprovedAmount());

            if (approvedAmount.compareTo(requestedAmount) > 0) {
                throw new ValidationException(
                        "Approved amount cannot exceed requested amount. "
                                + "Requested amount: ₹"
                                + requestedAmount
                                + ", approved amount: ₹"
                                + approvedAmount,
                        "ERR_APPROVED_AMOUNT_EXCEEDS_REQUESTED",
                        "approvedAmount"
                );
            }
        }

        // =====================================================
        // 6. RECHECK REMAINING INVOICEABLE AMOUNT
        // =====================================================
        BigDecimal estimateTotal =
                money(estimate.getGrandTotal());

        BigDecimal alreadyInvoicedAmount =
                getAlreadyInvoicedAdvanceAmount(
                        estimate.getId()
                );

        BigDecimal remainingInvoiceableAmount =
                estimateTotal
                        .subtract(alreadyInvoicedAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        validateRequestedAmount(
                approvedAmount,
                estimateTotal,
                remainingInvoiceableAmount,
                "approvedAmount"
        );

        // =====================================================
        // 7. APPROVE REQUEST
        // =====================================================
        request.setEstimate(estimate);
        request.setApprovedAmount(approvedAmount);
        request.setReviewedBy(approver);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewRemarks(
                clean(requestDto.getReviewRemarks())
        );
        request.setStatus(
                AdvanceTaxInvoiceRequestStatus.APPROVED
        );

        // =====================================================
        // 8. GENERATE ADVANCE TAX INVOICE
        // =====================================================
        Invoice generatedInvoice =
                invoiceService.generateAdvanceTaxInvoice(
                        request,
                        approver
                );

        if (generatedInvoice == null
                || generatedInvoice.getId() == null) {

            throw new ValidationException(
                    "Advance Tax Invoice generation failed",
                    "ERR_ADVANCE_INVOICE_GENERATION_FAILED",
                    "requestId"
            );
        }

        request.setInvoice(generatedInvoice);

        // =====================================================
        // 9. REUSE ORIGINAL PO UNBILLED — CREATE NOTHING NEW
        // =====================================================
        if (purchaseOrderConversion) {
            /*
             * The existing approved PO Unbilled record remains unchanged
             * as the original workflow record. The generated Advance Tax
             * Invoice is linked to that same Unbilled record.
             *
             * No new Unbilled Invoice is created.
             * No new Operation Project is created.
             */
            generatedInvoice.setUnbilledInvoice(
                    existingUnbilled
            );

            if (existingPoProject != null) {
                generatedInvoice.setOperationProjectNo(
                        existingPoProject.getProjectNo()
                );
            }

            /*
             * The project already exists for this PO. Marking this true
             * records that the Invoice is associated with the existing
             * project; it does not call Operation Service to create one.
             */
            generatedInvoice.setOperationSynced(true);
            generatedInvoice.setOperationSyncedAt(LocalDateTime.now());

            generatedInvoice.setOperationSyncStatus(
                    OperationSyncStatus.SYNCED
            );

            generatedInvoice.setOperationLastError(null);
            generatedInvoice.setOperationNextRetryAt(null);
            generatedInvoice.setOperationSyncAttempts(0);
            generatedInvoice.setOperationSyncedAt(
                    LocalDateTime.now()
            );
            generatedInvoice.setUpdatedBy(approver);
            generatedInvoice.setUpdatedAt(
                    LocalDateTime.now()
            );

            generatedInvoice =
                    invoiceRepository.saveAndFlush(
                            generatedInvoice
                    );

            existingUnbilled.setConvertedToAdvanceTaxInvoice(
                    true
            );
            existingUnbilled.setUpdatedBy(approver);
            existingUnbilled.setUpdatedAt(
                    LocalDateTime.now()
            );

            unbilledInvoiceRepository.save(
                    existingUnbilled
            );

            log.info(
                    "Completed PURCHASE_ORDER converted to Advance Tax Invoice "
                            + "| requestId={} | estimateId={} "
                            + "| unbilledId={} | unbilledNumber={} "
                            + "| invoiceId={} | invoiceNumber={} "
                            + "| projectNo={} | amount={} "
                            + "| noNewUnbilledCreated=true "
                            + "| noNewProjectCreated=true",
                    request.getId(),
                    estimate.getId(),
                    existingUnbilled.getId(),
                    existingUnbilled.getUnbilledNumber(),
                    generatedInvoice.getId(),
                    generatedInvoice.getInvoiceNumber(),
                    existingPoProject != null
                            ? existingPoProject.getProjectNo()
                            : null,
                    approvedAmount
            );
        }

        // =====================================================
        // 10. SAVE REQUEST AND RETURN
        // =====================================================
        AdvanceTaxInvoiceRequest savedRequest =
                advanceTaxInvoiceRequestRepository.save(
                        request
                );

        String message =
                purchaseOrderConversion
                        ? "Advance Tax Invoice approved and Invoice "
                        + generatedInvoice.getInvoiceNumber()
                        + " generated successfully for the complete "
                        + "PURCHASE_ORDER amount of ₹"
                        + approvedAmount
                        + ". Existing Unbilled Invoice "
                        + existingUnbilled.getUnbilledNumber()
                        + " was preserved and linked. No new Unbilled "
                        + "Invoice or Operation Project was created."
                        : "Advance Tax Invoice approved and Invoice "
                        + generatedInvoice.getInvoiceNumber()
                        + " generated successfully.";

        return mapToResponse(
                savedRequest,
                message
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AdvanceTaxInvoiceResponseDto getRequestById(
            Long requestId
    ) {

        if (requestId == null || requestId <= 0) {
            throw new ValidationException(
                    "Valid requestId is required",
                    "ERR_ADVANCE_REQUEST_ID_REQUIRED",
                    "requestId"
            );
        }

        AdvanceTaxInvoiceRequest request =
                advanceTaxInvoiceRequestRepository
                        .findById(requestId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Advance Tax Invoice request "
                                                + "not found with ID: "
                                                + requestId,
                                        "ADVANCE_TAX_INVOICE_REQUEST_NOT_FOUND",
                                        "AdvanceTaxInvoiceRequest",
                                        requestId
                                )
                        );

        return mapToResponse(request, null);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<AdvanceTaxInvoiceResponseDto> getRequests(
            Long requestingUserId,
            AdvanceTaxInvoiceRequestStatus status,
            int page,
            int size
    ) {

        // =====================================================
        // 1. VALIDATE REQUESTING USER ID
        // =====================================================

        if (requestingUserId == null || requestingUserId <= 0) {
            throw new ValidationException(
                    "Valid userId is required",
                    "ERR_INVALID_USER_ID",
                    "userId"
            );
        }

        // =====================================================
        // 2. FETCH ACTIVE REQUESTING USER
        // =====================================================

        User requestingUser =
                getActiveUser(
                        requestingUserId,
                        "userId"
                );

        // =====================================================
        // 3. DETERMINE USER ACCESS
        // =====================================================

        boolean accountsUser =
                belongsToAccountsDepartment(requestingUser);

        boolean adminUser =
                hasAdminRole(requestingUser);

        boolean salesUser =
                belongsToSalesDepartment(requestingUser);

        if (!accountsUser && !adminUser && !salesUser) {
            throw new ValidationException(
                    "Only Sales, Accounts or Admin users can view "
                            + "Advance Tax Invoice requests.",
                    "ERR_ADVANCE_INVOICE_LIST_ACCESS_DENIED",
                    "userId"
            );
        }

        /*
         * Accounts/Admin:
         * requestedByUserIdFilter = null
         * Therefore, all requests are returned.
         *
         * Sales:
         * requestedByUserIdFilter = requestingUserId
         * Therefore, only that salesperson's requests are returned.
         */
        Long requestedByUserIdFilter =
                accountsUser || adminUser
                        ? null
                        : requestingUser.getId();

        // =====================================================
        // 4. VALIDATE PAGINATION
        // =====================================================

        int safePage = Math.max(page, 0);

        int safeSize =
                size <= 0 || size > 200
                        ? 20
                        : size;

        Pageable pageable =
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );

        // =====================================================
        // 5. FETCH VISIBLE REQUESTS
        // =====================================================

        Page<AdvanceTaxInvoiceRequest> requestPage =
                advanceTaxInvoiceRequestRepository
                        .findVisibleRequests(
                                requestedByUserIdFilter,
                                status,
                                pageable
                        );

        // =====================================================
        // 6. MAP RESPONSE
        // =====================================================

        return requestPage.map(
                request -> mapToResponse(
                        request,
                        null
                )
        );
    }

    // =====================================================
    // VALIDATION
    // =====================================================

    private void validateCreateRequest(
            AdvanceTaxInvoiceCreateRequestDto requestDto
    ) {

        if (requestDto == null) {
            throw new ValidationException(
                    "Request body is required",
                    "ERR_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (requestDto.getEstimateId() == null
                || requestDto.getEstimateId() <= 0) {

            throw new ValidationException(
                    "Valid estimateId is required",
                    "ERR_INVALID_ESTIMATE_ID",
                    "estimateId"
            );
        }

        if (requestDto.getRequestedByUserId() == null
                || requestDto.getRequestedByUserId() <= 0) {

            throw new ValidationException(
                    "Valid requestedByUserId is required",
                    "ERR_INVALID_REQUESTED_BY",
                    "requestedByUserId"
            );
        }
    }

    private Estimate findEstimateForUpdate(
            Long estimateId
    ) {

        Estimate estimate =
                entityManager.find(
                        Estimate.class,
                        estimateId,
                        LockModeType.PESSIMISTIC_WRITE
                );

        if (estimate == null) {
            throw new ResourceNotFoundException(
                    "Estimate not found with ID: "
                            + estimateId,
                    "ESTIMATE_NOT_FOUND",
                    "Estimate",
                    estimateId
            );
        }

        return estimate;
    }

    private void validateEstimateForAdvanceInvoice(
            Estimate estimate
    ) {

        if (estimate == null) {
            throw new ValidationException(
                    "Estimate is required",
                    "ERR_ESTIMATE_REQUIRED",
                    "estimateId"
            );
        }

        if (estimate.isDeleted()) {
            throw new ValidationException(
                    "Advance Tax Invoice request cannot be created "
                            + "against a deleted Estimate.",
                    "ERR_ADVANCE_INVOICE_ON_DELETED_ESTIMATE",
                    "estimateId"
            );
        }

        if (estimate.isCancelled()) {
            throw new ValidationException(
                    "Advance Tax Invoice request cannot be created "
                            + "against a cancelled Estimate.",
                    "ERR_ADVANCE_INVOICE_ON_CANCELLED_ESTIMATE",
                    "estimateId"
            );
        }

        if (estimate.getStatus() == EstimateStatus.REJECTED) {
            throw new ValidationException(
                    "Advance Tax Invoice request cannot be created "
                            + "against a REJECTED Estimate.",
                    "ERR_ADVANCE_INVOICE_ON_REJECTED_ESTIMATE",
                    "estimateId"
            );
        }

        BigDecimal estimateTotal =
                money(estimate.getGrandTotal());

        if (estimateTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Estimate grand total must be greater than zero.",
                    "ERR_ESTIMATE_TOTAL_NOT_POSITIVE",
                    "estimateId"
            );
        }

        validateCompanyAndUnitApproval(estimate);
    }

    private void validateCompanyAndUnitApproval(
            Estimate estimate
    ) {

        Company company = estimate.getCompany();
        CompanyUnit unit = estimate.getUnit();

        boolean companyApproved =
                company != null
                        && !company.isDeleted()
                        && (
                        company.isAccountsApproved()
                                || (
                                company.getOnboardingStatus() != null
                                        && "APPROVED".equalsIgnoreCase(
                                        company.getOnboardingStatus().name()
                                )
                        )
                );

        boolean unitApproved =
                unit != null
                        && !unit.isDeleted()
                        && (
                        unit.isAccountsApproved()
                                || (
                                unit.getOnboardingStatus() != null
                                        && "APPROVED".equalsIgnoreCase(
                                        unit.getOnboardingStatus().name()
                                )
                        )
                );

        if (!companyApproved || !unitApproved) {
            throw new ValidationException(
                    "Company and Company Unit must both be approved "
                            + "by Accounts before raising or approving "
                            + "an Advance Tax Invoice request.",
                    "ERR_COMPANY_OR_UNIT_NOT_APPROVED_FOR_ADVANCE_INVOICE",
                    !companyApproved
                            ? "companyId"
                            : "unitId"
            );
        }
    }

    private User getActiveUser(
            Long userId,
            String field
    ) {

        User user =
                userRepository
                        .findByIdAndNotDeleted(userId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "User not found with ID: "
                                                + userId,
                                        "USER_NOT_FOUND",
                                        "User",
                                        userId
                                )
                        );

        if (!user.isActive()) {
            throw new ValidationException(
                    "Inactive user cannot perform this action.",
                    "ERR_INACTIVE_USER",
                    field
            );
        }

        return user;
    }

    private void validateAccountsOrAdmin(
            User approver
    ) {

        boolean accountsDepartment =
                approver.getDepartment() != null
                        && (
                        "ACCOUNT".equalsIgnoreCase(
                                approver.getDepartment().trim()
                        )
                                || "ACCOUNTS".equalsIgnoreCase(
                                approver.getDepartment().trim()
                        )
                );

        boolean adminRole =
                approver.getUserRole() != null
                        && approver.getUserRole()
                        .stream()
                        .filter(Objects::nonNull)
                        .anyMatch(role ->
                                !role.isDeleted()
                                        && role.getName() != null
                                        && "ADMIN".equalsIgnoreCase(
                                        role.getName().trim()
                                )
                        );

        if (!accountsDepartment && !adminRole) {
            throw new ValidationException(
                    "Only Accounts department or ADMIN users can "
                            + "approve an Advance Tax Invoice request.",
                    "ERR_ADVANCE_INVOICE_APPROVAL_ACCESS_DENIED",
                    "approverUserId"
            );
        }
    }

    private void validateRequestedAmount(
            BigDecimal amount,
            BigDecimal estimateTotal,
            BigDecimal remainingInvoiceableAmount,
            String field
    ) {

        BigDecimal safeAmount = money(amount);

        if (safeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    field + " must be greater than zero.",
                    "ERR_ADVANCE_INVOICE_AMOUNT_NOT_POSITIVE",
                    field
            );
        }

        if (remainingInvoiceableAmount
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new ValidationException(
                    "No remaining invoiceable amount is available "
                            + "for this Estimate.",
                    "ERR_NO_REMAINING_INVOICEABLE_AMOUNT",
                    field
            );
        }

        if (safeAmount.compareTo(
                remainingInvoiceableAmount
        ) > 0) {

            throw new ValidationException(
                    field
                            + " exceeds the remaining invoiceable amount. "
                            + "Requested/approved amount: ₹"
                            + safeAmount
                            + ", remaining invoiceable amount: ₹"
                            + remainingInvoiceableAmount,
                    "ERR_ADVANCE_INVOICE_AMOUNT_EXCEEDS_REMAINING",
                    field
            );
        }

        /*
         * Normal minimum = 25% of Estimate total.
         *
         * When the final remaining balance is less than 25%,
         * that exact smaller balance is allowed.
         */
        BigDecimal standardMinimum =
                estimateTotal
                        .multiply(
                                MINIMUM_REQUEST_PERCENTAGE
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal effectiveMinimum =
                standardMinimum.min(
                        remainingInvoiceableAmount
                );

        if (safeAmount.compareTo(effectiveMinimum) < 0) {
            throw new ValidationException(
                    field
                            + " must be at least ₹"
                            + effectiveMinimum
                            + ". The normal minimum is 25% of "
                            + "the Estimate total; a smaller amount "
                            + "is allowed only for the final balance.",
                    "ERR_ADVANCE_INVOICE_AMOUNT_BELOW_MINIMUM",
                    field
            );
        }
    }

    // =====================================================
    // PURCHASE ORDER CONVERSION
    // =====================================================

    private OperationProjectResponseDto
    validatePurchaseOrderConversionEligibility(
            Estimate estimate,
            UnbilledInvoice unbilled
    ) {
        // =====================================================
        // 1. ESTIMATE AND UNBILLED VALIDATION
        // =====================================================
        if (estimate == null || estimate.getId() == null) {
            throw new ValidationException(
                    "Estimate is required for PURCHASE_ORDER conversion",
                    "ERR_ESTIMATE_REQUIRED_FOR_PO_CONVERSION",
                    "estimateId"
            );
        }

        if (unbilled == null || unbilled.getId() == null) {
            throw new ValidationException(
                    "Existing Unbilled Invoice is required for "
                            + "PURCHASE_ORDER conversion",
                    "ERR_UNBILLED_REQUIRED_FOR_PO_CONVERSION",
                    "estimateId"
            );
        }

        if (unbilled.isCancelled()) {
            throw new ValidationException(
                    "The existing PURCHASE_ORDER Unbilled Invoice is cancelled",
                    "ERR_PO_UNBILLED_CANCELLED",
                    "unbilledId"
            );
        }

        if (unbilled.getEstimate() == null
                || unbilled.getEstimate().getId() == null
                || !Objects.equals(
                unbilled.getEstimate().getId(),
                estimate.getId()
        )) {
            throw new ValidationException(
                    "The existing Unbilled Invoice does not belong to "
                            + "Estimate "
                            + estimate.getEstimateNumber(),
                    "ERR_PO_UNBILLED_ESTIMATE_MISMATCH",
                    "estimateId"
            );
        }

        // =====================================================
        // 2. PO UNBILLED MUST BE APPROVED AND NOT CONVERTED
        // =====================================================
        if (unbilled.getStatus() != UnbilledStatus.APPROVED) {
            throw new ValidationException(
                    "The PURCHASE_ORDER Unbilled Invoice must first be "
                            + "approved by Accounts. Current status: "
                            + unbilled.getStatus(),
                    "ERR_PO_UNBILLED_NOT_APPROVED",
                    "unbilledId"
            );
        }

        if (unbilled.isConvertedToAdvanceTaxInvoice()) {
            throw new ValidationException(
                    "The existing PURCHASE_ORDER Unbilled Invoice "
                            + unbilled.getUnbilledNumber()
                            + " has already been converted to an "
                            + "Advance Tax Invoice.",
                    "ERR_PO_ALREADY_CONVERTED_TO_ADVANCE_INVOICE",
                    "unbilledId"
            );
        }

        // =====================================================
        // 3. FIRST ACTIVE RECEIPT MUST BE APPROVED ZERO-VALUE PO
        // =====================================================
        PaymentReceipt initialReceipt =
                findInitialActivePaymentReceipt(
                        unbilled
                );

        if (initialReceipt == null) {
            throw new ValidationException(
                    "Initial PURCHASE_ORDER receipt was not found for "
                            + "Unbilled Invoice "
                            + unbilled.getUnbilledNumber(),
                    "ERR_INITIAL_PO_RECEIPT_NOT_FOUND",
                    "unbilledId"
            );
        }

        if (initialReceipt.getPaymentType() == null
                || initialReceipt.getPaymentType().getCode() == null
                || !"PURCHASE_ORDER".equalsIgnoreCase(
                initialReceipt.getPaymentType()
                        .getCode()
                        .trim()
        )) {
            throw new ValidationException(
                    "The first active receipt is not a PURCHASE_ORDER receipt",
                    "ERR_INITIAL_PAYMENT_NOT_PURCHASE_ORDER",
                    "paymentTypeId"
            );
        }

        if (money(initialReceipt.getAmount())
                .compareTo(BigDecimal.ZERO) != 0) {

            throw new ValidationException(
                    "Only an initial zero-value PURCHASE_ORDER can use "
                            + "the project-completion Advance Tax Invoice flow. "
                            + "Initial PO amount: ₹"
                            + money(initialReceipt.getAmount()),
                    "ERR_INITIAL_PO_AMOUNT_NOT_ZERO",
                    "amount"
            );
        }

        if (initialReceipt.getStatus()
                != PaymentStatus.APPROVED) {

            throw new ValidationException(
                    "The initial PURCHASE_ORDER receipt must be approved "
                            + "by Accounts. Current status: "
                            + initialReceipt.getStatus(),
                    "ERR_INITIAL_PO_RECEIPT_NOT_APPROVED",
                    "paymentReceiptId"
            );
        }

        // =====================================================
        // 4. NO POSITIVE PAYMENT OR RECEIVED AMOUNT MAY EXIST
        // =====================================================
        if (hasActualPositivePayment(unbilled)) {
            throw new ValidationException(
                    "Advance Tax Invoice cannot be raised through the "
                            + "completed PURCHASE_ORDER flow because an actual "
                            + "positive payment already exists against Unbilled "
                            + unbilled.getUnbilledNumber(),
                    "ERR_ACTUAL_PAYMENT_ALREADY_EXISTS_FOR_PO",
                    "unbilledId"
            );
        }

        if (money(unbilled.getReceivedAmount())
                .compareTo(BigDecimal.ZERO) != 0) {

            throw new ValidationException(
                    "Advance Tax Invoice cannot be raised because the "
                            + "PURCHASE_ORDER Unbilled Invoice already has "
                            + "an approved received amount of ₹"
                            + money(unbilled.getReceivedAmount()),
                    "ERR_PO_UNBILLED_RECEIVED_AMOUNT_NOT_ZERO",
                    "unbilledId"
            );
        }

        if (money(unbilled.getCurrentReceivedAmount())
                .compareTo(BigDecimal.ZERO) != 0) {

            throw new ValidationException(
                    "Advance Tax Invoice cannot be raised because the "
                            + "PURCHASE_ORDER Unbilled Invoice has a pending "
                            + "received amount of ₹"
                            + money(unbilled.getCurrentReceivedAmount()),
                    "ERR_PO_UNBILLED_PENDING_AMOUNT_NOT_ZERO",
                    "unbilledId"
            );
        }

        // =====================================================
        // 5. NO TAX INVOICE MAY ALREADY EXIST
        // =====================================================
        if (hasAnyNonCancelledInvoice(estimate)) {
            throw new ValidationException(
                    "A non-cancelled Tax Invoice already exists for Estimate "
                            + estimate.getEstimateNumber(),
                    "ERR_TAX_INVOICE_ALREADY_EXISTS_FOR_PO_ESTIMATE",
                    "estimateId"
            );
        }

        // =====================================================
        // 6. EXISTING OPERATION PROJECT MUST BE BILLING-ELIGIBLE
        // =====================================================
        OperationProjectResponseDto project =
                validatePoProjectBillingEligibility(
                        unbilled
                );

        log.info(
                "PURCHASE_ORDER conversion eligibility passed "
                        + "| estimateId={} | estimateNumber={} "
                        + "| unbilledId={} | unbilledNumber={} "
                        + "| initialReceiptId={} | projectNo={} "
                        + "| poBillingEligible={}",
                estimate.getId(),
                estimate.getEstimateNumber(),
                unbilled.getId(),
                unbilled.getUnbilledNumber(),
                initialReceipt.getId(),
                project != null
                        ? project.getProjectNo()
                        : null,
                project != null
                        ? project.getPoBillingEligible()
                        : null
        );

        return project;
    }

    private PaymentReceipt findInitialActivePaymentReceipt(
            UnbilledInvoice unbilled
    ) {
        if (unbilled == null
                || unbilled.getPayments() == null
                || unbilled.getPayments().isEmpty()) {

            return null;
        }

        return unbilled.getPayments()
                .stream()
                .filter(Objects::nonNull)
                .filter(payment -> !payment.isCancelled())
                .filter(payment -> payment.getId() != null)
                .min(
                        Comparator.comparing(
                                PaymentReceipt::getId
                        )
                )
                .orElse(null);
    }

    private boolean hasActualPositivePayment(
            UnbilledInvoice unbilled
    ) {
        if (unbilled == null
                || unbilled.getPayments() == null
                || unbilled.getPayments().isEmpty()) {

            return false;
        }

        return unbilled.getPayments()
                .stream()
                .filter(Objects::nonNull)
                .filter(payment -> !payment.isCancelled())
                .filter(payment ->
                        payment.getStatus() == PaymentStatus.PENDING
                                || payment.getStatus()
                                == PaymentStatus.APPROVED
                )
                .anyMatch(payment ->
                        money(payment.getAmount())
                                .compareTo(BigDecimal.ZERO) > 0
                );
    }

    private boolean hasAnyNonCancelledInvoice(
            Estimate estimate
    ) {
        if (estimate == null || estimate.getId() == null) {
            return false;
        }

        Long invoiceCount =
                entityManager.createQuery(
                                """
                                select count(invoice)
                                from Invoice invoice
                                where invoice.estimate.id = :estimateId
                                  and invoice.isCancelled = false
                                """,
                                Long.class
                        )
                        .setParameter(
                                "estimateId",
                                estimate.getId()
                        )
                        .getSingleResult();

        return invoiceCount != null
                && invoiceCount > 0;
    }

    private OperationProjectResponseDto
    validatePoProjectBillingEligibility(
            UnbilledInvoice unbilled
    ) {
        if (unbilled == null
                || unbilled.getUnbilledNumber() == null
                || unbilled.getUnbilledNumber().isBlank()) {

            throw new ValidationException(
                    "Unbilled number is required to verify the "
                            + "PURCHASE_ORDER Operation Project",
                    "ERR_PO_UNBILLED_NUMBER_REQUIRED",
                    "unbilledNumber"
            );
        }

        try {
            ResponseEntity<OperationProjectResponseDto> response =
                    operationFeignClient
                            .getProjectByUnbilledNumber(
                                    unbilled.getUnbilledNumber()
                            );

            if (response == null
                    || !response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null) {

                throw new ValidationException(
                        "Operation Project details were not found for "
                                + "Unbilled Invoice "
                                + unbilled.getUnbilledNumber(),
                        "ERR_OPERATION_PROJECT_NOT_FOUND",
                        "unbilledNumber"
                );
            }

            OperationProjectResponseDto project =
                    response.getBody();

            log.info(
                    "PURCHASE_ORDER project status received "
                            + "| unbilledNumber={} | projectNo={} "
                            + "| poBillingEligible={}",
                    unbilled.getUnbilledNumber(),
                    project.getProjectNo(),
                    project.getPoBillingEligible()
            );

            if (!Boolean.TRUE.equals(
                    project.getPoBillingEligible()
            )) {
                throw new ValidationException(
                        "Advance Tax Invoice cannot be raised yet because "
                                + "Operation Project "
                                + (
                                project.getProjectNo() != null
                                        ? project.getProjectNo()
                                        : ""
                        )
                                + " is not completed or is not eligible for "
                                + "PURCHASE_ORDER billing. Complete all required "
                                + "non-Certification milestones first.",
                        "ERR_PO_PROJECT_NOT_READY_FOR_TAX_INVOICE",
                        "unbilledNumber"
                );
            }

            return project;

        } catch (FeignException.NotFound exception) {
            throw new ValidationException(
                    "Operation Project was not found for PURCHASE_ORDER "
                            + "Unbilled Invoice "
                            + unbilled.getUnbilledNumber(),
                    "ERR_OPERATION_PROJECT_NOT_FOUND",
                    "unbilledNumber"
            );

        } catch (ValidationException exception) {
            throw exception;

        } catch (FeignException exception) {
            throw new ValidationException(
                    "Unable to verify PURCHASE_ORDER project completion "
                            + "from Operation Service. HTTP status: "
                            + exception.status(),
                    "ERR_OPERATION_SERVICE_PO_ELIGIBILITY_CHECK_FAILED",
                    "unbilledNumber"
            );
        }
    }

    // =====================================================
    // AMOUNT QUERIES
    // =====================================================

    private BigDecimal getAlreadyInvoicedAdvanceAmount(
            Long estimateId
    ) {

        BigDecimal value =
                entityManager.createQuery(
                                """
                                select coalesce(sum(invoice.grandTotal), 0)
                                from Invoice invoice
                                where invoice.estimate.id = :estimateId
                                  and invoice.invoiceOrigin =
                                      com.account.domain.invoice.InvoiceOrigin.ADVANCE_TAX_INVOICE
                                  and invoice.isCancelled = false
                                """,
                                BigDecimal.class
                        )
                        .setParameter(
                                "estimateId",
                                estimateId
                        )
                        .getSingleResult();

        return money(value);
    }

    // =====================================================
    // RESPONSE MAPPING
    // =====================================================

    private AdvanceTaxInvoiceResponseDto mapToResponse(
            AdvanceTaxInvoiceRequest request,
            String message
    ) {

        Invoice invoice = request.getInvoice();
        Estimate estimate = request.getEstimate();

        Organization organization =
                organizationRepository
                        .findTopOrganization()
                        .orElse(null);

        GstRegistrationType gstRegistrationType =
                invoice != null && invoice.getGstRegistrationType() != null
                        ? invoice.getGstRegistrationType()
                        : estimate != null
                        && estimate.getUnit() != null
                        && estimate.getUnit().getGstRegistrationType() != null
                        ? estimate.getUnit().getGstRegistrationType()
                        : GstRegistrationType.REGISTERED;

        return AdvanceTaxInvoiceResponseDto.builder()

                // =====================================================
                // REQUEST
                // =====================================================

                .requestId(request.getId())
                .publicUuid(request.getPublicUuid())

                .requestedAmount(request.getRequestedAmount())
                .approvedAmount(request.getApprovedAmount())

                .requestStatus(request.getStatus())

                .requestRemarks(request.getRequestRemarks())
                .reviewRemarks(request.getReviewRemarks())

                .requestedByUserId(
                        request.getRequestedBy() != null
                                ? request.getRequestedBy().getId()
                                : null
                )
                .requestedByName(
                        resolveUserName(request.getRequestedBy())
                )

                .reviewedByUserId(
                        request.getReviewedBy() != null
                                ? request.getReviewedBy().getId()
                                : null
                )
                .reviewedByName(
                        resolveUserName(request.getReviewedBy())
                )

                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())

                // =====================================================
                // ESTIMATE
                // =====================================================

                .estimateId(
                        estimate != null
                                ? estimate.getId()
                                : null
                )
                .estimateNumber(
                        estimate != null
                                ? estimate.getEstimateNumber()
                                : null
                )
                .estimateGrandTotal(
                        estimate != null
                                ? estimate.getGrandTotal()
                                : null
                )
                .solutionId(
                        estimate != null
                                ? estimate.getSolutionId()
                                : null
                )
                .solutionName(
                        estimate != null
                                ? estimate.getSolutionName()
                                : null
                )

                // =====================================================
                // COMPANY / UNIT / CONTACT
                // =====================================================

                .companyId(
                        estimate != null
                                && estimate.getCompany() != null
                                ? estimate.getCompany().getId()
                                : null
                )
                .companyName(
                        estimate != null
                                && estimate.getCompany() != null
                                ? estimate.getCompany().getName()
                                : null
                )

                .unitId(
                        estimate != null
                                && estimate.getUnit() != null
                                ? estimate.getUnit().getId()
                                : null
                )
                .unitName(
                        estimate != null
                                && estimate.getUnit() != null
                                ? estimate.getUnit().getUnitName()
                                : null
                )

                .contactId(
                        estimate != null
                                && estimate.getContact() != null
                                ? estimate.getContact().getId()
                                : null
                )
                .contactName(
                        estimate != null
                                && estimate.getContact() != null
                                ? estimate.getContact().getName()
                                : null
                )

                // =====================================================
                // INVOICE
                // =====================================================

                .invoiceGenerated(invoice != null)

                .invoiceId(
                        invoice != null
                                ? invoice.getId()
                                : null
                )
                .invoicePublicUuid(
                        invoice != null
                                ? invoice.getPublicUuid()
                                : null
                )
                .invoiceNumber(
                        invoice != null
                                ? invoice.getInvoiceNumber()
                                : null
                )
                .unbilledNumber(
                        invoice != null
                                && invoice.getUnbilledInvoice() != null
                                ? invoice.getUnbilledInvoice().getUnbilledNumber()
                                : null
                )
                .invoiceOrigin(
                        invoice != null
                                ? invoice.getInvoiceOrigin()
                                : null
                )
                .invoiceDate(
                        invoice != null
                                ? invoice.getInvoiceDate()
                                : null
                )
                .currency(
                        invoice != null
                                ? invoice.getCurrency()
                                : null
                )
                .invoiceStatus(
                        invoice != null
                                ? invoice.getStatus()
                                : null
                )
                .placeOfSupplyStateCode(
                        invoice != null
                                ? invoice.getPlaceOfSupplyStateCode()
                                : null
                )
                .buyerGstin(
                        invoice != null
                                ? invoice.getBuyerGstin()
                                : null
                )
                .sellerGstin(
                        invoice != null
                                ? invoice.getOrganizationGstNo()
                                : organization != null
                                ? organization.getGstNo()
                                : null
                )
                .cancelled(
                        invoice != null
                                ? invoice.isCancelled()
                                : false
                )

                // =====================================================
                // GST
                // =====================================================

                .gstRegistrationType(gstRegistrationType.name())
                .gstApplicable(gstRegistrationType.isGstApplicable())
                .zeroRatedSupply(gstRegistrationType.isZeroRated())

                // =====================================================
                // FINANCIALS
                // =====================================================

                .subTotalExGst(
                        invoice != null
                                ? invoice.getSubTotalExGst()
                                : null
                )
                .totalGstAmount(
                        invoice != null
                                ? invoice.getTotalGstAmount()
                                : null
                )
                .cgstAmount(
                        invoice != null
                                ? invoice.getCgstAmount()
                                : null
                )
                .sgstAmount(
                        invoice != null
                                ? invoice.getSgstAmount()
                                : null
                )
                .igstAmount(
                        invoice != null
                                ? invoice.getIgstAmount()
                                : null
                )
                .invoiceGrandTotal(
                        invoice != null
                                ? invoice.getGrandTotal()
                                : null
                )

                // =====================================================
                // ORGANIZATION GENERAL DETAILS
                // Prefer invoice snapshot after invoice generation.
                // Otherwise use current organization configuration.
                // =====================================================

                .organizationName(
                        invoice != null
                                ? invoice.getOrganizationName()
                                : organization != null
                                ? organization.getName()
                                : null
                )
                .organizationAddressLine1(
                        invoice != null
                                ? invoice.getOrganizationAddressLine1()
                                : organization != null
                                ? organization.getAddressLine1()
                                : null
                )
                .organizationAddressLine2(
                        invoice != null
                                ? invoice.getOrganizationAddressLine2()
                                : organization != null
                                ? organization.getAddressLine2()
                                : null
                )
                .organizationCity(
                        invoice != null
                                ? invoice.getOrganizationCity()
                                : organization != null
                                ? organization.getCity()
                                : null
                )
                .organizationState(
                        invoice != null
                                ? invoice.getOrganizationState()
                                : organization != null
                                ? organization.getState()
                                : null
                )
                .organizationCountry(
                        invoice != null
                                ? invoice.getOrganizationCountry()
                                : organization != null
                                ? organization.getCountry()
                                : null
                )
                .organizationPinCode(
                        invoice != null
                                ? invoice.getOrganizationPinCode()
                                : organization != null
                                ? organization.getPinCode()
                                : null
                )
                .organizationGstNo(
                        invoice != null
                                ? invoice.getOrganizationGstNo()
                                : organization != null
                                ? organization.getGstNo()
                                : null
                )
                .organizationPanNo(
                        invoice != null
                                ? invoice.getOrganizationPanNo()
                                : organization != null
                                ? organization.getPanNo()
                                : null
                )
                .organizationCinNumber(
                        invoice != null
                                ? invoice.getOrganizationCinNumber()
                                : organization != null
                                ? organization.getCinNumber()
                                : null
                )
                .organizationEmail(
                        invoice != null
                                ? invoice.getOrganizationEmail()
                                : organization != null
                                ? organization.getEmail()
                                : null
                )
                .organizationPhone(
                        invoice != null
                                ? invoice.getOrganizationPhone()
                                : organization != null
                                ? organization.getPhone()
                                : null
                )
                .organizationWebsite(
                        invoice != null
                                ? invoice.getOrganizationWebsite()
                                : organization != null
                                ? organization.getWebsite()
                                : null
                )
                .organizationLogoUrl(
                        invoice != null
                                ? invoice.getOrganizationLogoUrl()
                                : organization != null
                                ? organization.getLogoUrl()
                                : null
                )

                // =====================================================
                // ORGANIZATION BANK DETAILS
                // Currently read from Organization master.
                // =====================================================

                .organizationBankAccountPresent(
                        organization != null
                                ? organization.isBankAccountPresent()
                                : null
                )
                .organizationAccountHolderName(
                        organization != null
                                ? organization.getAccountHolderName()
                                : null
                )
                .organizationAccountNumber(
                        organization != null
                                ? organization.getAccountNo()
                                : null
                )
                .organizationIfscCode(
                        organization != null
                                ? organization.getIfscCode()
                                : null
                )
                .organizationSwiftCode(
                        organization != null
                                ? organization.getSwiftCode()
                                : null
                )
                .organizationBankName(
                        organization != null
                                ? organization.getBankName()
                                : null
                )
                .organizationBankBranch(
                        organization != null
                                ? organization.getBranch()
                                : null
                )
                .organizationUpiId(
                        organization != null
                                ? organization.getUpiId()
                                : null
                )
                .organizationPaymentPageLink(
                        organization != null
                                ? organization.getPaymentPageLink()
                                : null
                )

                .message(message)
                .build();
    }
    private String resolveUserName(
            User user
    ) {

        if (user == null) {
            return null;
        }

        if (user.getFullName() != null
                && !user.getFullName().isBlank()) {

            return user.getFullName();
        }

        return user.getEmail();
    }

    // =====================================================
    // SMALL HELPERS
    // =====================================================

    private BigDecimal money(
            BigDecimal value
    ) {

        return (
                value == null
                        ? BigDecimal.ZERO
                        : value
        ).setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private String clean(
            String value
    ) {

        if (value == null) {
            return null;
        }

        String cleaned = value.trim();

        return cleaned.isEmpty()
                ? null
                : cleaned;
    }


    private boolean belongsToAccountsDepartment(User user) {

        if (user == null || user.getDepartment() == null) {
            return false;
        }

        String department =
                user.getDepartment().trim();

        return "ACCOUNT".equalsIgnoreCase(department)
                || "ACCOUNTS".equalsIgnoreCase(department);
    }

    private boolean belongsToSalesDepartment(User user) {

        if (user == null || user.getDepartment() == null) {
            return false;
        }

        String department =
                user.getDepartment().trim();

        return "SALE".equalsIgnoreCase(department)
                || "SALES".equalsIgnoreCase(department);
    }

    private boolean hasAdminRole(User user) {

        if (user == null || user.getUserRole() == null) {
            return false;
        }

        return user.getUserRole()
                .stream()
                .filter(Objects::nonNull)
                .filter(role -> !role.isDeleted())
                .filter(role -> role.getName() != null)
                .map(role -> role.getName().trim())
                .anyMatch(roleName ->
                        "ADMIN".equalsIgnoreCase(roleName)
                                || "SUPER_ADMIN".equalsIgnoreCase(roleName)
                );
    }



    @Override
    @Transactional
    public ConfirmAdvanceInvoiceResponseDto confirmEInvoiceAndCreateProject(
            Long invoiceId,
            ConfirmInvoiceEInvoiceRequestDto request
    ) {
        // =====================================================
        // 1. BASIC VALIDATION
        // =====================================================
        if (invoiceId == null || invoiceId <= 0) {
            throw new ValidationException(
                    "Valid invoiceId is required",
                    "ERR_ADVANCE_INVOICE_NOT_FOUND",
                    "invoiceId"
            );
        }

        if (request == null) {
            throw new ValidationException(
                    "Confirmation request is required",
                    "ERR_E_INVOICE_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (request.getUserId() == null
                || request.getUserId() <= 0) {

            throw new ValidationException(
                    "User ID is required",
                    "ERR_USER_NOT_FOUND",
                    "userId"
            );
        }

        log.info(
                "Advance Invoice confirmation started | invoiceId={} | userId={}",
                invoiceId,
                request.getUserId()
        );

        // =====================================================
        // 2. FETCH INVOICE WITH PESSIMISTIC LOCK
        // =====================================================
        Invoice invoice = invoiceRepository
                .findByIdForAdvanceEInvoiceConfirmation(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Advance Tax Invoice not found with ID: " + invoiceId,
                        "ERR_ADVANCE_INVOICE_NOT_FOUND",
                        "Invoice",
                        invoiceId
                ));

        validateAdvanceInvoiceForConfirmation(invoice);

        // =====================================================
        // 3. AUTHORIZE USER
        // =====================================================
        User confirmedBy = getActiveUser(
                request.getUserId(),
                "userId"
        );

        validateAccountsOrAdminForEInvoice(confirmedBy);

        // =====================================================
        // 4. RESOLVE GST ROUTE
        // =====================================================
        GstRegistrationType gstType =
                resolveAdvanceInvoiceGstRegistrationType(invoice);

        boolean eInvoiceRequired =
                isEInvoiceRequired(gstType);

        String incomingIrn =
                clean(request.getEInvoiceIrn());

        log.info(
                "Advance Invoice GST route resolved | "
                        + "invoiceId={} | gstType={} | "
                        + "eInvoiceRequired={} | incomingIrnPresent={}",
                invoiceId,
                gstType,
                eInvoiceRequired,
                hasText(incomingIrn)
        );

        // =====================================================
        // 5. IDEMPOTENT PROCESSING
        // =====================================================
        if (isLocallyFinalized(invoice, eInvoiceRequired)) {

            if (eInvoiceRequired
                    && hasText(incomingIrn)
                    && hasText(invoice.getEInvoiceIrn())
                    && !invoice.getEInvoiceIrn()
                    .equalsIgnoreCase(incomingIrn)) {

                throw new ValidationException(
                        "Advance Tax Invoice is already confirmed "
                                + "with another IRN",
                        "ERR_E_INVOICE_ALREADY_CONFIRMED_WITH_DIFFERENT_IRN",
                        "eInvoiceIrn"
                );
            }

            /*
             * Ensure the Sales Invoice voucher exists.
             * This is idempotent and will not create a duplicate.
             */
            postAdvanceInvoiceSalesVoucherExactlyOnce(
                    invoice,
                    invoice.getEstimate(),
                    confirmedBy
            );

            return buildConfirmResponse(
                    invoice,
                    gstType,
                    eInvoiceRequired,
                    "Advance Tax Invoice was already processed. "
                            + "Sales Voucher is available. "
                            + "No Operation Project was created."
            );
        }

        // =====================================================
        // 6. REGISTERED / SEZ E-INVOICE CONFIRMATION
        // =====================================================
        if (eInvoiceRequired) {

            validateConditionalEInvoiceFields(request);

            if (invoiceRepository
                    .existsByEInvoiceIrnExcludingInvoice(
                            incomingIrn,
                            invoice.getId()
                    )) {

                throw new ValidationException(
                        "The supplied IRN is already assigned "
                                + "to another active Invoice",
                        "ERR_DUPLICATE_E_INVOICE_IRN",
                        "eInvoiceIrn"
                );
            }

            invoice.setEInvoiceIrn(incomingIrn);

            invoice.setEInvoiceAckNo(
                    clean(request.getEInvoiceAckNo())
            );

            invoice.setEInvoiceAckDate(
                    request.getEInvoiceAckDate()
            );

            invoice.setEInvoiceAttachmentUrl(
                    clean(request.getEInvoiceAttachmentUrl())
            );

            invoice.setEInvoiceConfirmedBy(confirmedBy);
            invoice.setEInvoiceConfirmedAt(LocalDateTime.now());

            invoice.setEInvoiceRemarks(
                    clean(request.getRemarks())
            );

            invoice.setStatus(
                    InvoiceStatus.E_INVOICE_CONFIRMED
            );

            log.info(
                    "Advance Invoice e-invoice confirmed | "
                            + "invoiceId={} | invoiceNumber={} | irn={}",
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    maskIrn(incomingIrn)
            );

        } else {
            // =====================================================
            // 7. UNREGISTERED / INTERNATIONAL FINALIZATION
            // =====================================================
            invoice.setEInvoiceIrn(null);
            invoice.setEInvoiceAckNo(null);
            invoice.setEInvoiceAckDate(null);
            invoice.setEInvoiceAttachmentUrl(null);
            invoice.setEInvoiceConfirmedBy(null);
            invoice.setEInvoiceConfirmedAt(null);
            invoice.setEInvoiceRemarks(null);

            invoice.setFinalizedAt(LocalDateTime.now());
            invoice.setFinalizedBy(confirmedBy);

            invoice.setFinalizationRemarks(
                    clean(request.getRemarks())
            );

            invoice.setStatus(
                    InvoiceStatus.FINALIZED_WITHOUT_E_INVOICE
            );

            log.info(
                    "E-invoice skipped for Advance Invoice | "
                            + "invoiceId={} | gstType={}",
                    invoice.getId(),
                    gstType
            );
        }

        // =====================================================
        // 8. DO NOT CREATE/SYNCHRONIZE OPERATION PROJECT
        // =====================================================
        invoice.setUpdatedBy(confirmedBy);
        invoice.setUpdatedAt(LocalDateTime.now());

        /*
         * This API does not create an Operation Project.
         *
         * Do not set:
         * operationSynced = false
         * operationSyncStatus = PENDING
         * operationNextRetryAt
         *
         * Existing operation fields are left unchanged.
         */

        invoice = invoiceRepository.saveAndFlush(invoice);

        // =====================================================
        // 9. POST SALES VOUCHER EXACTLY ONCE
        // =====================================================
        postAdvanceInvoiceSalesVoucherExactlyOnce(
                invoice,
                invoice.getEstimate(),
                confirmedBy
        );

        boolean voucherPosted =
                accountingVoucherService.existsPostedVoucher(
                        VoucherType.SALES_INVOICE,
                        VoucherSourceType.INVOICE,
                        invoice.getId()
                );

        if (!voucherPosted) {
            throw new ValidationException(
                    "Advance Tax Invoice was confirmed, but "
                            + "the Sales Voucher could not be verified",
                    "ERR_SALES_VOUCHER_POSTING_FAILED",
                    "invoiceId"
            );
        }

        // =====================================================
        // 10. RESPONSE
        // =====================================================
        String message;

        if (eInvoiceRequired) {
            message =
                    "Advance Tax Invoice e-invoice confirmed successfully. "
                            + "Sales Voucher posted successfully. "
                            + "No Operation Project was created.";
        } else {
            message =
                    "E-invoice was not required for "
                            + gstType
                            + ". Advance Tax Invoice finalized successfully. "
                            + "Sales Voucher posted successfully. "
                            + "No Operation Project was created.";
        }

        return buildConfirmResponse(
                invoice,
                gstType,
                eInvoiceRequired,
                message
        );
    }

    private void validateAdvanceInvoiceForConfirmation(Invoice invoice) {
        if (invoice.isCancelled()) {
            throw new ValidationException(
                    "Cancelled Invoice cannot be confirmed",
                    "ERR_CANNOT_CONFIRM_CANCELLED_INVOICE",
                    "invoiceId"
            );
        }

        if (invoice.getInvoiceOrigin() != InvoiceOrigin.ADVANCE_TAX_INVOICE) {
            throw new ValidationException(
                    "This API supports only Advance Tax Invoices",
                    "ERR_NOT_AN_ADVANCE_TAX_INVOICE",
                    "invoiceId"
            );
        }

        if (invoice.getEstimate() == null) {
            throw new ValidationException(
                    "Estimate is missing from Advance Tax Invoice",
                    "ERR_ADVANCE_INVOICE_ESTIMATE_MISSING",
                    "invoiceId"
            );
        }

        if (invoice.getAdvanceTaxInvoiceRequest() == null
                || invoice.getAdvanceTaxInvoiceRequest().getStatus()
                != AdvanceTaxInvoiceRequestStatus.APPROVED) {

            throw new ValidationException(
                    "Advance Tax Invoice request must be APPROVED",
                    "ERR_ADVANCE_REQUEST_NOT_APPROVED",
                    "invoiceId"
            );
        }

        if (money(invoice.getGrandTotal()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Advance Tax Invoice grand total must be greater than zero",
                    "ERR_ADVANCE_INVOICE_AMOUNT_INVALID",
                    "invoiceId"
            );
        }

        if (invoice.getLineItems() == null || invoice.getLineItems().isEmpty()) {
            throw new ValidationException(
                    "Advance Tax Invoice line items are missing",
                    "ERR_INVOICE_LINE_ITEMS_MISSING",
                    "invoiceId"
            );
        }
    }

    private GstRegistrationType resolveAdvanceInvoiceGstRegistrationType(
            Invoice invoice
    ) {
        if (invoice.getGstRegistrationType() != null) {
            return invoice.getGstRegistrationType();
        }

        Estimate estimate = invoice.getEstimate();

        if (estimate != null
                && estimate.getUnit() != null
                && estimate.getUnit().getGstRegistrationType() != null) {
            return estimate.getUnit().getGstRegistrationType();
        }

        throw new ValidationException(
                "GST registration type is missing on Invoice and Company Unit",
                "ERR_GST_REGISTRATION_TYPE_MISSING",
                "invoiceId"
        );
    }

    private boolean isEInvoiceRequired(GstRegistrationType gstType) {
        return gstType == GstRegistrationType.REGISTERED
                || gstType == GstRegistrationType.SEZ;
    }

    private void validateConditionalEInvoiceFields(
            ConfirmInvoiceEInvoiceRequestDto request
    ) {
        if (!hasText(request.getEInvoiceAttachmentUrl())) {
            throw new ValidationException(
                    "E-invoice attachment URL is required",
                    "ERR_E_INVOICE_ATTACHMENT_REQUIRED",
                    "eInvoiceAttachmentUrl"
            );
        }

        if (!hasText(request.getEInvoiceIrn())) {
            throw new ValidationException(
                    "E-invoice IRN is required",
                    "ERR_E_INVOICE_IRN_REQUIRED",
                    "eInvoiceIrn"
            );
        }

        if (!hasText(request.getEInvoiceAckNo())) {
            throw new ValidationException(
                    "E-invoice acknowledgement number is required",
                    "ERR_E_INVOICE_ACK_NO_REQUIRED",
                    "eInvoiceAckNo"
            );
        }

        if (request.getEInvoiceAckDate() == null) {
            throw new ValidationException(
                    "E-invoice acknowledgement date is required",
                    "ERR_E_INVOICE_ACK_DATE_REQUIRED",
                    "eInvoiceAckDate"
            );
        }
    }

    private void validateAccountsOrAdminForEInvoice(User user) {
        boolean accounts = user.getDepartment() != null
                && "accounts".equalsIgnoreCase(user.getDepartment().trim());

        boolean admin = user.getUserRole() != null
                && user.getUserRole().stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.getName() != null
                        && "ADMIN".equalsIgnoreCase(role.getName().trim()));

        if (!accounts && !admin) {
            throw new ValidationException(
                    "Only Accounts or Admin users can confirm an e-invoice",
                    "ERR_USER_NOT_AUTHORIZED_FOR_E_INVOICE",
                    "userId"
            );
        }
    }

    private boolean isLocallyFinalized(
            Invoice invoice,
            boolean eInvoiceRequired
    ) {
        if (eInvoiceRequired) {
            return invoice.getStatus() == InvoiceStatus.E_INVOICE_CONFIRMED
                    && hasText(invoice.getEInvoiceIrn());
        }

        return invoice.getStatus() == InvoiceStatus.FINALIZED_WITHOUT_E_INVOICE
                || invoice.getFinalizedAt() != null;
    }

    private void postAdvanceInvoiceSalesVoucherExactlyOnce(
            Invoice invoice,
            Estimate estimate,
            User confirmedBy
    ) {
        if (accountingVoucherService.existsPostedVoucher(
                VoucherType.SALES_INVOICE,
                VoucherSourceType.INVOICE,
                invoice.getId()
        )) {
            log.info(
                    "Advance Invoice Sales Voucher already exists | invoiceId={}",
                    invoice.getId()
            );
            return;
        }

        try {
            LedgerMaster customerLedger =
                    getOrCreateCustomerLedgerFromEstimate(estimate, confirmedBy);

            LedgerMaster serviceIncomeLedger = getOrCreateSystemLedger(
                    LedgerType.SERVICE_INCOME,
                    LedgerGroupType.SALES_ACCOUNTS,
                    "Service Income",
                    DebitCredit.CREDIT,
                    confirmedBy
            );

            BigDecimal grandTotal = money(invoice.getGrandTotal());
            BigDecimal taxable = money(invoice.getSubTotalExGst());
            BigDecimal cgst = money(invoice.getCgstAmount());
            BigDecimal sgst = money(invoice.getSgstAmount());
            BigDecimal igst = money(invoice.getIgstAmount());

            List<AccountingVoucherEntryRequestDto> entries = new ArrayList<>();

            entries.add(buildVoucherEntry(
                    customerLedger.getId(),
                    grandTotal,
                    BigDecimal.ZERO,
                    "Customer receivable for Advance Tax Invoice "
                            + invoice.getInvoiceNumber()
            ));

            entries.add(buildVoucherEntry(
                    serviceIncomeLedger.getId(),
                    BigDecimal.ZERO,
                    taxable,
                    "Service income for Advance Tax Invoice "
                            + invoice.getInvoiceNumber()
            ));

            if (cgst.compareTo(BigDecimal.ZERO) > 0) {
                LedgerMaster ledger = getOrCreateSystemLedger(
                        LedgerType.OUTPUT_CGST,
                        LedgerGroupType.DUTIES_AND_TAXES,
                        "Output CGST",
                        DebitCredit.CREDIT,
                        confirmedBy
                );
                entries.add(buildVoucherEntry(
                        ledger.getId(),
                        BigDecimal.ZERO,
                        cgst,
                        "Output CGST for " + invoice.getInvoiceNumber()
                ));
            }

            if (sgst.compareTo(BigDecimal.ZERO) > 0) {
                LedgerMaster ledger = getOrCreateSystemLedger(
                        LedgerType.OUTPUT_SGST,
                        LedgerGroupType.DUTIES_AND_TAXES,
                        "Output SGST",
                        DebitCredit.CREDIT,
                        confirmedBy
                );
                entries.add(buildVoucherEntry(
                        ledger.getId(),
                        BigDecimal.ZERO,
                        sgst,
                        "Output SGST for " + invoice.getInvoiceNumber()
                ));
            }

            if (igst.compareTo(BigDecimal.ZERO) > 0) {
                LedgerMaster ledger = getOrCreateSystemLedger(
                        LedgerType.OUTPUT_IGST,
                        LedgerGroupType.DUTIES_AND_TAXES,
                        "Output IGST",
                        DebitCredit.CREDIT,
                        confirmedBy
                );
                entries.add(buildVoucherEntry(
                        ledger.getId(),
                        BigDecimal.ZERO,
                        igst,
                        "Output IGST for " + invoice.getInvoiceNumber()
                ));
            }

            AccountingVoucherRequestDto voucherRequest =
                    AccountingVoucherRequestDto.builder()
                            .voucherType(VoucherType.SALES_INVOICE)
                            .voucherDate(invoice.getInvoiceDate() != null
                                    ? invoice.getInvoiceDate()
                                    : LocalDate.now())
                            .sourceType(VoucherSourceType.INVOICE)
                            .sourceId(invoice.getId())
                            .narration(
                                    "Advance Tax Invoice posted: "
                                            + invoice.getInvoiceNumber()
                            )
                            .entries(entries)
                            .build();

            accountingVoucherService.createVoucher(voucherRequest);

        } catch (ValidationException ex) {
            throw ex;

        } catch (Exception ex) {

            log.error(
                    "Unable to post Advance Tax Invoice Sales Voucher "
                            + "| invoiceId={} | error={}",
                    invoice != null ? invoice.getId() : null,
                    ex.getMessage(),
                    ex
            );

            throw new ValidationException(
                    "Unable to post Sales Voucher for Advance Tax Invoice",
                    "ERR_SALES_VOUCHER_POSTING_FAILED",
                    "invoiceId"
            );
        }
    }

    private LedgerMaster getOrCreateCustomerLedgerFromEstimate(
            Estimate estimate,
            User createdBy
    ) {
        if (estimate == null
                || estimate.getCompany() == null
                || estimate.getCompany().getId() == null) {
            throw new ValidationException(
                    "Company is required to resolve customer ledger",
                    "ERR_COMPANY_REQUIRED_FOR_LEDGER",
                    "companyId"
            );
        }

        Long companyId = estimate.getCompany().getId();

        Optional<LedgerMaster> existing =
                ledgerMasterRepository.findByCompanyIdAndLedgerTypeAndDeletedFalse(
                        companyId,
                        LedgerType.CUSTOMER
                );

        if (existing.isPresent()) {
            return existing.get();
        }

        LedgerGroup debtors = ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(LedgerGroupType.SUNDRY_DEBTORS)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sundry Debtors ledger group not found",
                        "SUNDRY_DEBTORS_GROUP_NOT_FOUND"
                ));

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerName(
                hasText(estimate.getCompany().getName())
                        ? estimate.getCompany().getName().trim()
                        : "Company-" + companyId
        );
        ledger.setLedgerCode(
                "CUST-" + companyId
        );
        ledger.setLedgerType(LedgerType.CUSTOMER);
        ledger.setLedgerGroup(debtors);
        ledger.setCompany(estimate.getCompany());
        ledger.setUnit(estimate.getUnit());
        ledger.setContact(estimate.getContact());
        ledger.setOpeningBalance(BigDecimal.ZERO.setScale(2));
        ledger.setOpeningBalanceType(DebitCredit.DEBIT);
        ledger.setCurrentBalance(BigDecimal.ZERO.setScale(2));
        ledger.setCurrentBalanceType(DebitCredit.DEBIT);
        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);
        ledger.setCreatedBy(createdBy);
        ledger.setUpdatedBy(createdBy);

        return ledgerMasterRepository.save(ledger);
    }

    private ConfirmAdvanceInvoiceResponseDto buildConfirmResponse(
            Invoice invoice,
            GstRegistrationType gstType,
            boolean eInvoiceRequired,
            String message
    ) {
        boolean voucherPosted =
                accountingVoucherService.existsPostedVoucher(
                        VoucherType.SALES_INVOICE,
                        VoucherSourceType.INVOICE,
                        invoice.getId()
                );

        return ConfirmAdvanceInvoiceResponseDto.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceOrigin(invoice.getInvoiceOrigin())
                .gstRegistrationType(gstType)
                .eInvoiceRequired(eInvoiceRequired)
                .eInvoiceConfirmed(
                        eInvoiceRequired
                                && invoice.getStatus()
                                == InvoiceStatus.E_INVOICE_CONFIRMED
                )
                .eInvoiceIrn(
                        eInvoiceRequired
                                ? invoice.getEInvoiceIrn()
                                : null
                )
                .eInvoiceAckNo(
                        eInvoiceRequired
                                ? invoice.getEInvoiceAckNo()
                                : null
                )
                .eInvoiceAckDate(
                        eInvoiceRequired
                                ? invoice.getEInvoiceAckDate()
                                : null
                )
                .salesVoucherPosted(voucherPosted)

                /*
                 * Normal Advance Tax Invoice: no Operation Project is created.
                 * Completed PO conversion: the already-existing PO project is reused.
                 */
                .operationSynced(invoice.isOperationSynced())
                .operationProjectNo(invoice.getOperationProjectNo())
                .operationSyncStatus(
                        hasText(invoice.getOperationProjectNo())
                                ? "EXISTING_PROJECT_REUSED"
                                : "NOT_APPLICABLE"
                )

                .message(message)
                .build();
    }

    private String maskIrn(String irn) {
        if (!hasText(irn) || irn.length() <= 8) {
            return "********";
        }
        return irn.substring(0, 4)
                + "..."
                + irn.substring(irn.length() - 4);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private AccountingVoucherEntryRequestDto buildVoucherEntry(
            Long ledgerId,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String narration
    ) {
        if (ledgerId == null) {
            throw new ValidationException(
                    "Ledger ID is required for voucher entry",
                    "ERR_VOUCHER_LEDGER_REQUIRED",
                    "ledgerId"
            );
        }

        BigDecimal safeDebitAmount = money(debitAmount);
        BigDecimal safeCreditAmount = money(creditAmount);

        if (safeDebitAmount.compareTo(BigDecimal.ZERO) < 0
                || safeCreditAmount.compareTo(BigDecimal.ZERO) < 0) {

            throw new ValidationException(
                    "Voucher debit and credit amounts cannot be negative",
                    "ERR_INVALID_VOUCHER_ENTRY_AMOUNT",
                    "amount"
            );
        }

        if (safeDebitAmount.compareTo(BigDecimal.ZERO) > 0
                && safeCreditAmount.compareTo(BigDecimal.ZERO) > 0) {

            throw new ValidationException(
                    "A voucher entry cannot contain both debit and credit amounts",
                    "ERR_VOUCHER_ENTRY_HAS_DEBIT_AND_CREDIT",
                    "amount"
            );
        }

        if (safeDebitAmount.compareTo(BigDecimal.ZERO) == 0
                && safeCreditAmount.compareTo(BigDecimal.ZERO) == 0) {

            throw new ValidationException(
                    "Voucher entry must contain either a debit or credit amount",
                    "ERR_EMPTY_VOUCHER_ENTRY",
                    "amount"
            );
        }

        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledgerId)
                .debitAmount(safeDebitAmount)
                .creditAmount(safeCreditAmount)
                .narration(clean(narration))
                .build();
    }

    private LedgerMaster getOrCreateSystemLedger(
            LedgerType ledgerType,
            LedgerGroupType ledgerGroupType,
            String ledgerName,
            DebitCredit balanceType,
            User createdBy
    ) {
        if (ledgerType == null) {
            throw new ValidationException(
                    "Ledger type is required",
                    "ERR_LEDGER_TYPE_REQUIRED",
                    "ledgerType"
            );
        }

        if (ledgerGroupType == null) {
            throw new ValidationException(
                    "Ledger group type is required",
                    "ERR_LEDGER_GROUP_TYPE_REQUIRED",
                    "ledgerGroupType"
            );
        }

        Optional<LedgerMaster> existingLedger =
                ledgerMasterRepository.findByLedgerTypeAndDeletedFalse(
                        ledgerType
                );

        if (existingLedger.isPresent()) {
            LedgerMaster ledger = existingLedger.get();

            if (!ledger.isActive()) {
                ledger.setActive(true);
                ledger.setUpdatedBy(createdBy);
                ledger = ledgerMasterRepository.save(ledger);
            }

            log.debug(
                    "System ledger reused | ledgerType={} | ledgerId={}",
                    ledgerType,
                    ledger.getId()
            );

            return ledger;
        }

        LedgerGroup ledgerGroup =
                ledgerGroupRepository
                        .findByGroupTypeAndDeletedFalse(
                                ledgerGroupType
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        ledgerGroupType
                                                + " ledger group not found",
                                        ledgerGroupType
                                                + "_GROUP_NOT_FOUND"
                                )
                        );

        LedgerMaster ledger = new LedgerMaster();

        ledger.setLedgerName(
                hasText(ledgerName)
                        ? ledgerName.trim()
                        : formatLedgerTypeName(ledgerType)
        );

        ledger.setLedgerCode(
                generateSystemLedgerCode(ledgerType)
        );

        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setOpeningBalance(
                BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP
                )
        );

        ledger.setOpeningBalanceType(balanceType);

        ledger.setCurrentBalance(
                BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP
                )
        );

        ledger.setCurrentBalanceType(balanceType);

        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        if (createdBy != null) {
            ledger.setCreatedBy(createdBy);
            ledger.setUpdatedBy(createdBy);
        }

        LedgerMaster savedLedger =
                ledgerMasterRepository.save(ledger);

        log.info(
                "System ledger created | ledgerType={} | ledgerId={} | ledgerName={}",
                ledgerType,
                savedLedger.getId(),
                savedLedger.getLedgerName()
        );

        return savedLedger;
    }


    private String generateSystemLedgerCode(
            LedgerType ledgerType
    ) {
        String prefix = switch (ledgerType) {
            case SERVICE_INCOME -> "LED-SERVICE-";
            case OUTPUT_CGST -> "LED-OUT-CGST-";
            case OUTPUT_SGST -> "LED-OUT-SGST-";
            case OUTPUT_IGST -> "LED-OUT-IGST-";
            case TDS_RECEIVABLE -> "LED-TDS-REC-";
            case TDS_PAYABLE -> "LED-TDS-PAY-";
            case INPUT_CGST -> "LED-IN-CGST-";
            case INPUT_SGST -> "LED-IN-SGST-";
            case INPUT_IGST -> "LED-IN-IGST-";
            default -> "LED-SYS-";
        };

        String ledgerCode;

        do {
            ledgerCode =
                    prefix
                            + System.currentTimeMillis()
                            + "-"
                            + UUID.randomUUID()
                            .toString()
                            .substring(0, 6)
                            .toUpperCase();

        } while (
                ledgerMasterRepository
                        .existsByLedgerCodeIgnoreCase(
                                ledgerCode
                        )
        );

        return ledgerCode;
    }

    private String formatLedgerTypeName(
            LedgerType ledgerType
    ) {
        if (ledgerType == null) {
            return "System Ledger";
        }

        return Arrays.stream(
                        ledgerType.name()
                                .toLowerCase()
                                .split("_")
                )
                .filter(word -> !word.isBlank())
                .map(word ->
                        Character.toUpperCase(
                                word.charAt(0)
                        ) + word.substring(1)
                )
                .reduce(
                        (first, second) ->
                                first + " " + second
                )
                .orElse("System Ledger");
    }


}
