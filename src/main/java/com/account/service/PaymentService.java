package com.account.service;

import com.account.domain.UnbilledStatus;
import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceDetailDto;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;

import java.util.List;

public interface PaymentService {

    PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId);

    UnbilledInvoiceApprovalResponseDto approveUnbilledInvoice(Long unbilledId, UnbilledInvoiceApprovalRequestDto request);

    List<UnbilledInvoiceSummaryDto> getUnbilledInvoicesList(Long userId, UnbilledStatus status, int page, int size);

    long getUnbilledInvoicesCount(Long userId, UnbilledStatus status);

    List<UnbilledInvoiceSummaryDto> searchUnbilledInvoices(String unbilledNumber, String companyName, int i, int size);

    long countSearchUnbilledInvoices(String unbilledNumber, String companyName);

    UnbilledInvoiceDetailDto getUnbilledInvoice(Long id, Long userId);
}