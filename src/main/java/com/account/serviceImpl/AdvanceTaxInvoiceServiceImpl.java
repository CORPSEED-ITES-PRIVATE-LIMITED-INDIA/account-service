package com.account.serviceImpl;

import com.account.domain.PaymentReceipt;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.estimate.Estimate;
import com.account.domain.estimate.EstimateStatus;
import com.account.domain.invoice.AdvanceTaxInvoiceRequest;
import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.PaymentStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.invoice.AdvanceTaxInvoiceApprovalRequestDto;
import com.account.dto.invoice.AdvanceTaxInvoiceCreateRequestDto;
import com.account.dto.invoice.AdvanceTaxInvoiceResponseDto;
import com.account.dto.operationService.OperationProjectResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.feignClient.OperationFeignClient;
import com.account.repository.AdvanceTaxInvoiceRequestRepository;
import com.account.repository.UnbilledInvoiceRepository;
import com.account.repository.UserRepository;
import com.account.service.AdvanceTaxInvoiceService;
import com.account.service.InvoiceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdvanceTaxInvoiceServiceImpl
        implements AdvanceTaxInvoiceService {

    private static final BigDecimal MINIMUM_REQUEST_PERCENTAGE =
            new BigDecimal("0.25");

    private final AdvanceTaxInvoiceRequestRepository
            advanceTaxInvoiceRequestRepository;

    private final UserRepository userRepository;

    private final UnbilledInvoiceRepository
            unbilledInvoiceRepository;

    private final InvoiceService invoiceService;

    private final OperationFeignClient operationFeignClient;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public AdvanceTaxInvoiceResponseDto createRequest(
            AdvanceTaxInvoiceCreateRequestDto requestDto
    ) {

        validateCreateRequest(requestDto);

        /*
         * Estimate is the common parent resource.
         *
         * Locking it serializes:
         * - concurrent Advance Invoice requests
         * - concurrent request approvals
         * - remaining invoiceable amount calculations
         */
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

        /*
         * Normal rule:
         * An active UnbilledInvoice means the payment-first flow has started.
         *
         * Special exception:
         * - the first active receipt is an APPROVED zero-value PURCHASE_ORDER
         * - no actual positive payment exists
         * - no Invoice exists
         * - Operation Project is eligible for PO billing
         * - the Unbilled record has not already been converted
         *
         * In that exception, the request amount is system-calculated and
         * manual amount entry is not allowed.
         */
        UnbilledInvoice existingUnbilled =
                unbilledInvoiceRepository
                        .findByEstimateAndIsCancelledFalse(
                                estimate
                        )
                        .orElse(null);

        boolean purchaseOrderConversion =
                isPurchaseOrderCompletedConversionEligible(
                        estimate,
                        existingUnbilled
                );

        if (existingUnbilled != null
                && !purchaseOrderConversion) {

            throw new ValidationException(
                    "Advance Tax Invoice request cannot be created because "
                            + "the normal payment-first workflow has already "
                            + "started for Estimate "
                            + estimate.getEstimateNumber()
                            + ". Only an approved zero-value PURCHASE_ORDER "
                            + "with PO billing eligibility and no actual payment "
                            + "can be converted.",
                    "ERR_NORMAL_PAYMENT_FLOW_ALREADY_STARTED",
                    "estimateId"
            );
        }

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

        if (purchaseOrderConversion) {

            if (requestDto.getRequestedAmount() != null) {
                throw new ValidationException(
                        "requestedAmount must not be entered manually for "
                                + "a completed zero-value PURCHASE_ORDER. "
                                + "The system will use the complete remaining "
                                + "invoiceable amount.",
                        "ERR_MANUAL_AMOUNT_NOT_ALLOWED_FOR_PO_CONVERSION",
                        "requestedAmount"
                );
            }

            requestedAmount = remainingInvoiceableAmount;

            if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "No remaining invoiceable amount is available for "
                                + "the PURCHASE_ORDER conversion.",
                        "ERR_NO_REMAINING_INVOICEABLE_AMOUNT",
                        "estimateId"
                );
            }

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

        return mapToResponse(
                saved,
                "Advance Tax Invoice request created successfully "
                        + "and is awaiting Accounts approval."
        );
    }

    @Override
    @Transactional
    public AdvanceTaxInvoiceResponseDto approveRequest(
            Long requestId,
            AdvanceTaxInvoiceApprovalRequestDto requestDto
    ) {

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

        if (requestDto.getApproverUserId() == null) {
            throw new ValidationException(
                    "approverUserId is required",
                    "ERR_APPROVER_USER_REQUIRED",
                    "approverUserId"
            );
        }

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

        User approver =
                getActiveUser(
                        requestDto.getApproverUserId(),
                        "approverUserId"
                );

        validateAccountsOrAdmin(approver);

        Estimate estimate =
                findEstimateForUpdate(
                        request.getEstimate().getId()
                );

        validateEstimateForAdvanceInvoice(estimate);

        /*
         * Re-evaluate the PO conversion conditions during approval because
         * project/payment state may have changed after request creation.
         */
        UnbilledInvoice existingUnbilled =
                unbilledInvoiceRepository
                        .findByEstimateAndIsCancelledFalse(
                                estimate
                        )
                        .orElse(null);

        boolean purchaseOrderConversion =
                isPurchaseOrderCompletedConversionEligible(
                        estimate,
                        existingUnbilled
                );

        if (existingUnbilled != null
                && !purchaseOrderConversion) {

            throw new ValidationException(
                    "Advance Tax Invoice request cannot be approved because "
                            + "the normal payment-first workflow is active "
                            + "and the PURCHASE_ORDER conversion conditions "
                            + "are no longer satisfied.",
                    "ERR_NORMAL_PAYMENT_FLOW_ALREADY_STARTED",
                    "estimateId"
            );
        }

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
                                + "amount of ₹" + requestedAmount + ".",
                        "ERR_PO_CONVERSION_PARTIAL_APPROVAL_NOT_ALLOWED",
                        "approvedAmount"
                );
            }

            approvedAmount = requestedAmount;

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

        request.setEstimate(estimate);
        request.setApprovedAmount(approvedAmount);
        request.setReviewedBy(approver);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewRemarks(
                clean(requestDto.getReviewRemarks())
        );

        /*
         * Set APPROVED before invoice generation.
         *
         * Both operations are in the same transaction, so any
         * Invoice generation failure rolls this status back.
         */
        request.setStatus(
                AdvanceTaxInvoiceRequestStatus.APPROVED
        );

        Invoice generatedInvoice =
                invoiceService.generateAdvanceTaxInvoice(
                        request,
                        approver
                );

        /*
         * Inverse-side assignment keeps the in-memory graph aligned.
         * The owning foreign key is Invoice.advanceTaxInvoiceRequest.
         */
        request.setInvoice(generatedInvoice);

        if (purchaseOrderConversion
                && existingUnbilled != null) {

            existingUnbilled.setConvertedToAdvanceTaxInvoice(true);
            existingUnbilled.setUpdatedBy(approver);
            existingUnbilled.setUpdatedAt(LocalDateTime.now());

            unbilledInvoiceRepository.save(existingUnbilled);
        }

        AdvanceTaxInvoiceRequest savedRequest =
                advanceTaxInvoiceRequestRepository.save(
                        request
                );

        return mapToResponse(
                savedRequest,
                "Advance Tax Invoice approved and Invoice "
                        + generatedInvoice.getInvoiceNumber()
                        + " generated successfully."
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

    private boolean isPurchaseOrderCompletedConversionEligible(
            Estimate estimate,
            UnbilledInvoice unbilled
    ) {

        if (estimate == null
                || unbilled == null
                || unbilled.isCancelled()
                || unbilled.isConvertedToAdvanceTaxInvoice()) {

            return false;
        }

        if (!isApprovedInitialZeroValuePurchaseOrder(unbilled)) {
            return false;
        }

        if (hasActualPositivePayment(unbilled)) {
            return false;
        }

        if (money(unbilled.getReceivedAmount())
                .compareTo(BigDecimal.ZERO) != 0) {

            return false;
        }

        if (money(unbilled.getCurrentReceivedAmount())
                .compareTo(BigDecimal.ZERO) != 0) {

            return false;
        }

        if (hasAnyNonCancelledInvoice(estimate)) {
            return false;
        }

        return isPoBillingEligibleInOperationService(unbilled);
    }

    private boolean isApprovedInitialZeroValuePurchaseOrder(
            UnbilledInvoice unbilled
    ) {

        if (unbilled == null
                || unbilled.getPayments() == null
                || unbilled.getPayments().isEmpty()) {

            return false;
        }

        PaymentReceipt firstActiveReceipt =
                unbilled.getPayments()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(payment -> !payment.isCancelled())
                        .min(
                                Comparator.comparing(
                                        PaymentReceipt::getId,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()
                                        )
                                )
                        )
                        .orElse(null);

        if (firstActiveReceipt == null
                || firstActiveReceipt.getPaymentType() == null
                || firstActiveReceipt.getPaymentType().getCode() == null) {

            return false;
        }

        boolean purchaseOrder =
                "PURCHASE_ORDER".equalsIgnoreCase(
                        firstActiveReceipt
                                .getPaymentType()
                                .getCode()
                                .trim()
                );

        boolean zeroValue =
                money(firstActiveReceipt.getAmount())
                        .compareTo(BigDecimal.ZERO) == 0;

        boolean approved =
                firstActiveReceipt.getStatus()
                        == PaymentStatus.APPROVED;

        return purchaseOrder
                && zeroValue
                && approved;
    }

    private boolean hasActualPositivePayment(
            UnbilledInvoice unbilled
    ) {

        if (unbilled == null
                || unbilled.getPayments() == null) {

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

    private boolean isPoBillingEligibleInOperationService(
            UnbilledInvoice unbilled
    ) {

        if (unbilled == null
                || unbilled.getUnbilledNumber() == null
                || unbilled.getUnbilledNumber().isBlank()) {

            return false;
        }

        try {
            ResponseEntity<OperationProjectResponseDto> response =
                    operationFeignClient
                            .getProjectByUnbilledNumber(
                                    unbilled.getUnbilledNumber()
                            );

            if (response == null
                    || !response.getStatusCode()
                    .is2xxSuccessful()
                    || response.getBody() == null) {

                return false;
            }

            OperationProjectResponseDto project =
                    response.getBody();

            return Boolean.TRUE.equals(
                    project.getPoBillingEligible()
            );

        } catch (FeignException.NotFound ex) {

            return false;

        } catch (FeignException ex) {

            throw new ValidationException(
                    "Unable to verify PURCHASE_ORDER project billing "
                            + "eligibility from Operation Service. "
                            + "Status: " + ex.status(),
                    "ERR_OPERATION_SERVICE_PO_ELIGIBILITY_CHECK_FAILED",
                    "estimateId"
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

        Estimate estimate =
                request.getEstimate();

        Invoice invoice =
                request.getInvoice();

        return AdvanceTaxInvoiceResponseDto.builder()
                .requestId(request.getId())
                .publicUuid(request.getPublicUuid())

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
                                ? money(
                                estimate.getGrandTotal()
                        )
                                : null
                )

                .requestedAmount(
                        money(
                                request.getRequestedAmount()
                        )
                )
                .approvedAmount(
                        request.getApprovedAmount() != null
                                ? money(
                                request.getApprovedAmount()
                        )
                                : null
                )
                .requestStatus(
                        request.getStatus()
                )

                .requestedByUserId(
                        request.getRequestedBy() != null
                                ? request.getRequestedBy().getId()
                                : null
                )
                .requestedByName(
                        resolveUserName(
                                request.getRequestedBy()
                        )
                )

                .reviewedByUserId(
                        request.getReviewedBy() != null
                                ? request.getReviewedBy().getId()
                                : null
                )
                .reviewedByName(
                        resolveUserName(
                                request.getReviewedBy()
                        )
                )

                .invoiceId(
                        invoice != null
                                ? invoice.getId()
                                : null
                )
                .invoiceNumber(
                        invoice != null
                                ? invoice.getInvoiceNumber()
                                : null
                )
                .invoiceGrandTotal(
                        invoice != null
                                ? money(
                                invoice.getGrandTotal()
                        )
                                : null
                )

                .receivedAmount(
                        invoice != null
                                ? money(
                                invoice.getReceivedAmount()
                        )
                                : null
                )
                .pendingReceivedAmount(
                        invoice != null
                                ? money(
                                invoice.getPendingReceivedAmount()
                        )
                                : null
                )
                .availableOutstandingAmount(
                        invoice != null
                                ? money(
                                invoice.getAvailableOutstandingAmount()
                        )
                                : null
                )
                .outstandingAmount(
                        invoice != null
                                ? money(
                                invoice.getOutstandingAmount()
                        )
                                : null
                )
                .invoicePaymentStatus(
                        invoice != null
                                ? invoice.getPaymentStatus()
                                : null
                )

                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
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


}
