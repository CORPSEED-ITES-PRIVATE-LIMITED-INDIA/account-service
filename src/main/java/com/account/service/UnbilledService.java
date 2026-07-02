package com.account.service;

import com.account.domain.status.UnbilledStatus;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;

import java.time.LocalDate;
import java.util.List;

public interface UnbilledService {

    List<UnbilledInvoiceSummaryDto> getUnbilledReport(
            Long userId,
            Long createdByUserId,
            UnbilledStatus status,
            LocalDate fromDate,
            LocalDate toDate
    );


    long getUnbilledReportCount(
            Long userId,
            Long createdByUserId,
            UnbilledStatus status,
            LocalDate fromDate,
            LocalDate toDate
    );

    List<UnbilledInvoiceSummaryDto> getUnbilledInvoicesList(Long userId, UnbilledStatus status, int page, int size);


    void cancelUnbilled(Long userId, Long id, String reason, String cancelAttachment);

    void requestCancelUnbilled(Long userId, Long id, String reason, String cancelAttachment);

    void approveCancelUnbilled(Long adminUserId, Long id);

    void rejectCancelUnbilled(Long adminUserId, Long id, String reason);
}