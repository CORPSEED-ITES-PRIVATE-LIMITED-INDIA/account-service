package com.account.service;

import com.account.domain.status.UnbilledStatus;
import com.account.dto.CancelUnbilledAndEstimateRequestDto;
import com.account.dto.RefundRequestDto;
import com.account.dto.unbilled.UnbilledInvoiceSummaryDto;
import com.account.dto.unbilled.UnbilledProjectCompletionDto;

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

    UnbilledInvoiceSummaryDto issueRefund(String unbilledNumber, RefundRequestDto request);

    void cancelUnbilledWithEstimate(String unbilledNumber, CancelUnbilledAndEstimateRequestDto req);

    List<UnbilledInvoiceSummaryDto> getCancelRequests(
            Long adminUserId,
            int page,
            int size
    );

    /** ADMIN only. Number of pending cancellation requests. */
    long getCancelRequestsCount(Long adminUserId);

    /** ADMIN only. Project completion details for a cancellation request. */
    UnbilledProjectCompletionDto getProjectCompletionForCancellation(
            Long adminUserId,
            Long unbilledId
    );
}