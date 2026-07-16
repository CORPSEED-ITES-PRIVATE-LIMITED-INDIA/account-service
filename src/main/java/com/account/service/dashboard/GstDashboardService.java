package com.account.service.dashboard;

import com.account.dto.dashboard.GstCollectedSummaryDto;

import com.account.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class GstDashboardService {

    private final InvoiceRepository invoiceRepository;

    @Transactional(readOnly = true)
    public GstCollectedSummaryDto getGstCollectedSummary(
            Long userId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "User id is required"
            );
        }

        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException(
                    "From date and to date are required"
            );
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException(
                    "From date cannot be after to date"
            );
        }

        GstCollectedSummaryDto response =
                invoiceRepository.findGstCollectedSummary(
                        userId,
                        fromDate,
                        toDate
                );

        if (response == null) {
            return new GstCollectedSummaryDto(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        return response;
    }
}