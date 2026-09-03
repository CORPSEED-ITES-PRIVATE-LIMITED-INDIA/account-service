package com.account.service;

import com.account.domain.PaymentReceipt;
import com.account.domain.User;
import com.account.dto.payment.PaymentLegalSummaryResponseDto;
import com.account.dto.payment.PaymentLegalVerificationResponseDto;
import com.account.dto.payment.ReviewPaymentLegalVerificationRequestDto;

import java.util.List;

public interface PaymentLegalVerificationService {

    void createIfPurchaseOrder(PaymentReceipt receipt, User requestedBy);

    List<PaymentLegalVerificationResponseDto> getPendingRequests(Long userId);

    List<PaymentLegalVerificationResponseDto> getRequestsByUnbilled(Long unbilledId);

    PaymentLegalVerificationResponseDto reviewRequest(
            Long requestId,
            Long reviewedById,
            ReviewPaymentLegalVerificationRequestDto request
    );
    PaymentLegalSummaryResponseDto getSummary(Long userId);

    void validatePurchaseOrderLegalApprovalBeforeAccountsApproval(PaymentReceipt receipt);
}