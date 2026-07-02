package com.account.serviceImpl;

import com.account.domain.*;
import com.account.domain.estimate.Estimate;
import com.account.domain.payment.PaymentLegalVerificationRequest;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.payment.PaymentLegalVerificationResponseDto;
import com.account.dto.payment.ReviewPaymentLegalVerificationRequestDto;
import com.account.enm.PaymentLegalVerificationStatus;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.PaymentLegalVerificationRequestRepository;
import com.account.repository.PaymentReceiptRepository;
import com.account.repository.UnbilledInvoiceRepository;
import com.account.repository.UserRepository;
import com.account.service.PaymentLegalVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.FORBIDDEN;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentLegalVerificationServiceImpl implements PaymentLegalVerificationService {

    private final PaymentLegalVerificationRequestRepository legalRequestRepository;
    private final UserRepository userRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;

    @Override
    public void createIfPurchaseOrder(PaymentReceipt receipt, User requestedBy) {

        if (receipt == null || receipt.getId() == null || receipt.getPaymentType() == null) {
            return;
        }

        String paymentTypeCode = receipt.getPaymentType().getCode() != null
                ? receipt.getPaymentType().getCode().trim().toUpperCase()
                : "";

        if (!"PURCHASE_ORDER".equals(paymentTypeCode)) {
            return;
        }

        if (!StringUtils.hasText(receipt.getPaymentProof())) {
            throw new ValidationException(
                    "PO attachment is required for Purchase Order payment",
                    "ERR_PO_ATTACHMENT_REQUIRED",
                    "paymentProof"
            );
        }

        PaymentLegalVerificationRequest legalRequest =
                legalRequestRepository
                        .findByPaymentReceiptAndIsDeletedFalse(receipt)
                        .orElseGet(PaymentLegalVerificationRequest::new);

        UnbilledInvoice unbilled = receipt.getUnbilledInvoice();
        Estimate estimate = unbilled != null ? unbilled.getEstimate() : null;

        legalRequest.setPaymentReceipt(receipt);
        legalRequest.setUnbilledInvoice(unbilled);
        legalRequest.setEstimate(estimate);
        legalRequest.setCompany(unbilled != null ? unbilled.getCompany() : null);
        legalRequest.setUnit(unbilled != null ? unbilled.getUnit() : null);
        legalRequest.setPoAttachmentUrl(receipt.getPaymentProof());
        legalRequest.setPaymentTermsDays(receipt.getPaymentTermsDays());
        legalRequest.setPaymentTerms(receipt.getPaymentTerms());
        legalRequest.setRequestedBy(requestedBy);
        legalRequest.setStatus(PaymentLegalVerificationStatus.PENDING);
        legalRequest.setReviewedBy(null);
        legalRequest.setReviewedAt(null);
        legalRequest.setReviewRemark(null);

        legalRequestRepository.save(legalRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentLegalVerificationResponseDto> getPendingRequests(Long userId) {

        User user = getActiveUser(userId);

        if (!isAdmin(user) && !isLegalDepartment(user)) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Only Legal department or Admin can view PO legal verification requests"
            );
        }

        return legalRequestRepository
                .findByStatusAndIsDeletedFalseOrderByCreatedAtDesc(PaymentLegalVerificationStatus.PENDING)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentLegalVerificationResponseDto> getRequestsByUnbilled(Long unbilledId) {

        UnbilledInvoice unbilled = unbilledInvoiceRepository.findById(unbilledId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unbilled invoice not found with ID: " + unbilledId,
                        "UNBILLED_NOT_FOUND",
                        "UnbilledInvoice",
                        unbilledId
                ));

        return legalRequestRepository
                .findByUnbilledInvoiceAndIsDeletedFalseOrderByCreatedAtDesc(unbilled)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentLegalVerificationResponseDto reviewRequest(
            Long requestId,
            Long reviewedById,
            ReviewPaymentLegalVerificationRequestDto request
    ) {

        if (request == null || request.getApprove() == null) {
            throw new ValidationException(
                    "approve is required",
                    "ERR_APPROVE_REQUIRED",
                    "approve"
            );
        }

        if (!request.getApprove() && !StringUtils.hasText(request.getRemark())) {
            throw new ValidationException(
                    "Remark is required when rejecting PO legal verification",
                    "ERR_REMARK_REQUIRED",
                    "remark"
            );
        }

        User reviewedBy = getActiveUser(reviewedById);

        if (!isAdmin(reviewedBy) && !isLegalDepartment(reviewedBy)) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Only Legal department or Admin can approve/reject PO legal verification requests"
            );
        }

        PaymentLegalVerificationRequest legalRequest =
                legalRequestRepository.findById(requestId)
                        .filter(r -> !r.isDeleted())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "PO legal verification request not found",
                                "PO_LEGAL_REQUEST_NOT_FOUND",
                                "PaymentLegalVerificationRequest",
                                requestId
                        ));

        if (legalRequest.getStatus() != PaymentLegalVerificationStatus.PENDING) {
            throw new ValidationException(
                    "Only pending PO legal request can be reviewed",
                    "ERR_PO_LEGAL_REQUEST_ALREADY_REVIEWED",
                    "status"
            );
        }

        PaymentLegalVerificationStatus finalStatus =
                request.getApprove()
                        ? PaymentLegalVerificationStatus.APPROVED
                        : PaymentLegalVerificationStatus.REJECTED;

        legalRequest.setStatus(finalStatus);
        legalRequest.setReviewedBy(reviewedBy);
        legalRequest.setReviewedAt(LocalDateTime.now());
        legalRequest.setReviewRemark(request.getRemark());

        legalRequest = legalRequestRepository.save(legalRequest);

        return mapToDto(legalRequest);
    }

    @Override
    public void validatePurchaseOrderLegalApprovalBeforeAccountsApproval(PaymentReceipt receipt) {

        if (receipt == null || receipt.getPaymentType() == null) {
            return;
        }

        String paymentTypeCode = receipt.getPaymentType().getCode() != null
                ? receipt.getPaymentType().getCode().trim().toUpperCase()
                : "";

        if (!"PURCHASE_ORDER".equals(paymentTypeCode)) {
            return;
        }

        PaymentLegalVerificationRequest legalRequest =
                legalRequestRepository
                        .findByPaymentReceiptAndIsDeletedFalse(receipt)
                        .orElseThrow(() -> new ValidationException(
                                "PO legal verification request is missing for this Purchase Order payment",
                                "ERR_PO_LEGAL_REQUEST_MISSING",
                                "paymentReceiptId"
                        ));

        if (legalRequest.getStatus() == PaymentLegalVerificationStatus.REJECTED) {
            throw new ValidationException(
                    "Accounts approval is blocked because PO document is rejected by Legal. Remark: "
                            + nullSafe(legalRequest.getReviewRemark()),
                    "ERR_PO_LEGAL_REJECTED",
                    "paymentReceiptId"
            );
        }

        if (legalRequest.getStatus() != PaymentLegalVerificationStatus.APPROVED) {
            throw new ValidationException(
                    "Accounts approval is blocked until Legal approves the PO document",
                    "ERR_PO_LEGAL_APPROVAL_PENDING",
                    "paymentReceiptId"
            );
        }
    }

    private User getActiveUser(Long userId) {
        return userRepository.findByIdAndNotDeleted(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with ID: " + userId,
                        "USER_NOT_FOUND",
                        "User",
                        userId
                ));
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null &&
                user.getRole().stream()
                        .anyMatch(role -> "ADMIN".equalsIgnoreCase(role.trim()));
    }

    private boolean isLegalDepartment(User user) {
        return user.getDepartment() != null &&
                user.getDepartment().trim().equalsIgnoreCase("Legal");
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private PaymentLegalVerificationResponseDto mapToDto(PaymentLegalVerificationRequest request) {

        PaymentLegalVerificationResponseDto dto = new PaymentLegalVerificationResponseDto();

        dto.setId(request.getId());

        if (request.getPaymentReceipt() != null) {
            dto.setPaymentReceiptId(request.getPaymentReceipt().getId());
        }

        if (request.getUnbilledInvoice() != null) {
            dto.setUnbilledInvoiceId(request.getUnbilledInvoice().getId());
            dto.setUnbilledNumber(request.getUnbilledInvoice().getUnbilledNumber());
        }

        if (request.getEstimate() != null) {
            dto.setEstimateId(request.getEstimate().getId());
            dto.setEstimateNumber(request.getEstimate().getEstimateNumber());
            dto.setSolutionName(request.getEstimate().getSolutionName());
        }

        if (request.getCompany() != null) {
            dto.setCompanyId(request.getCompany().getId());
            dto.setCompanyName(request.getCompany().getName());
        }

        if (request.getUnit() != null) {
            dto.setUnitId(request.getUnit().getId());
            dto.setUnitName(request.getUnit().getUnitName());
        }

        dto.setPoAttachmentUrl(request.getPoAttachmentUrl());
        dto.setPaymentTermsDays(request.getPaymentTermsDays());
        dto.setPaymentTerms(request.getPaymentTerms());

        dto.setStatus(request.getStatus() != null ? request.getStatus().name() : null);

        if (request.getRequestedBy() != null) {
            dto.setRequestedById(request.getRequestedBy().getId());
            dto.setRequestedByName(request.getRequestedBy().getFullName());
        }

        if (request.getReviewedBy() != null) {
            dto.setReviewedById(request.getReviewedBy().getId());
            dto.setReviewedByName(request.getReviewedBy().getFullName());
        }

        dto.setReviewedAt(request.getReviewedAt());
        dto.setReviewRemark(request.getReviewRemark());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setUpdatedAt(request.getUpdatedAt());

        return dto;
    }
}