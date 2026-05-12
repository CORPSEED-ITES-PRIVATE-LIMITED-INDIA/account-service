package com.account.service;

import com.account.domain.UnbilledStatus;
import com.account.dto.operationService.OperationProjectActivityResponseDto;
import com.account.dto.payment.GovernmentFeeResponseDto;
import com.account.dto.payment.PaymentRegistrationRequestDto;
import com.account.dto.payment.PaymentRegistrationResponseDto;
import com.account.dto.payment.TdsResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceApprovalResponseDto;
import com.account.dto.unbilled.UnbilledInvoiceDetailDto;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PaymentService {

    PaymentRegistrationResponseDto registerPayment(PaymentRegistrationRequestDto request, Long salespersonUserId);

    UnbilledInvoiceApprovalResponseDto updateUnbilledInvoiceStatus(Long unbilledId, UnbilledInvoiceApprovalRequestDto request);

    List<UnbilledInvoiceSummaryDto> getUnbilledInvoicesList(Long userId, UnbilledStatus status, int page, int size);

    long getUnbilledInvoicesCount(Long userId, UnbilledStatus status);

    List<UnbilledInvoiceSummaryDto> searchUnbilledInvoices(String unbilledNumber, String companyName, int i, int size);

    long countSearchUnbilledInvoices(String unbilledNumber, String companyName);

    UnbilledInvoiceDetailDto getUnbilledInvoice(Long id, Long userId);

    void rejectUnbilledInvoice(Long unbilledId, String trim, Long approverUserId);

    void cancelUnbilled(Long userId, Long id,String reason);

    Page<OperationProjectActivityResponseDto> getExpences(Long userId, Long unbilledId,  Pageable pageable);

    void approveExpense(Long userId, Long unbilledId, Long expenseId, String status);

    UnbilledInvoiceDetailDto convertIntoADI(Long unbilledId,Long requestingUserId);

    GovernmentFeeResponseDto getGovernmentFee(Long unbilledId, Long estimateId);

    TdsResponseDto getTds(Long unbilledId, Long estimateId);

    UnbilledInvoiceDetailDto getUnbilledInvoiceByNumber(String unbilledNumber, Long requestingUserId);
}