package com.account.service;

import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;

public interface PaymentService {

    PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId);

    UnbilledInvoiceApprovalResponseDto approveUnbilledInvoice(Long unbilledId, UnbilledInvoiceApprovalRequestDto request);
}