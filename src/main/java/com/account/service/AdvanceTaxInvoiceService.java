package com.account.service;

import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import com.account.dto.invoice.AdvanceTaxInvoiceApprovalRequestDto;
import com.account.dto.invoice.AdvanceTaxInvoiceCreateRequestDto;
import com.account.dto.invoice.AdvanceTaxInvoiceResponseDto;
import org.springframework.data.domain.Page;

public interface AdvanceTaxInvoiceService {

    AdvanceTaxInvoiceResponseDto createRequest(
            AdvanceTaxInvoiceCreateRequestDto requestDto
    );

    AdvanceTaxInvoiceResponseDto approveRequest(
            Long requestId,
            AdvanceTaxInvoiceApprovalRequestDto requestDto
    );

    AdvanceTaxInvoiceResponseDto getRequestById(
            Long requestId
    );

    Page<AdvanceTaxInvoiceResponseDto> getRequests(
            Long requestingUserId,
            AdvanceTaxInvoiceRequestStatus status,
            int page,
            int size
    );


}
