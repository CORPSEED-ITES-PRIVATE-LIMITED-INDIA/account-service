package com.account.service;

import com.account.domain.UnbilledStatus;
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


}