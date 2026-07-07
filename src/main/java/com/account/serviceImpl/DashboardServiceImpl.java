package com.account.serviceImpl;

import com.account.domain.status.InvoiceStatus;
import com.account.dto.dashboard.TopConvertedLeadItemDto;
import com.account.dto.dashboard.TopConvertedLeadsResponseDto;
import com.account.dto.dashboard.TopSellingServiceItemDto;
import com.account.dto.dashboard.TopSellingServicesResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.InvoiceRepository;
import com.account.repository.UserRepository;
import com.account.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public TopSellingServicesResponseDto getTopSellingServices(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required and must be positive");
        }

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "User not found with ID: " + userId,
                    "USER_NOT_FOUND"
            );
        }

        DateRange dateRange = resolveDateRange(period, fromDate, toDate);
        int safeLimit = resolveLimit(limit);

        Pageable pageable = PageRequest.of(0, safeLimit);

        List<TopSellingServiceItemDto> items =
                invoiceRepository.findTopSellingServicesForSalesperson(
                        userId,
                        InvoiceStatus.GENERATED,
                        dateRange.fromDate(),
                        dateRange.toDate(),
                        pageable
                );

        /*
         * If old estimates do not have leadId, leadCount may come as 0.
         * In that case, fallback to invoiceCount so the UI does not show blank/zero.
         */
        items.forEach(item -> {
            if ((item.getLeadCount() == null || item.getLeadCount() == 0)
                    && item.getInvoiceCount() != null
                    && item.getInvoiceCount() > 0) {
                item.setLeadCount(item.getInvoiceCount());
            }
        });

        return TopSellingServicesResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .limit(safeLimit)
                .topSellingServices(items)
                .build();
    }

    private int resolveLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 3;
        }

        return Math.min(limit, 20);
    }

    private DateRange resolveDateRange(String period, LocalDate fromDate, LocalDate toDate) {
        LocalDate today = LocalDate.now();

        if (fromDate != null || toDate != null) {
            if (fromDate == null || toDate == null) {
                throw new IllegalArgumentException("Both fromDate and toDate are required for custom date range");
            }

            validateDateRange(fromDate, toDate);
            return new DateRange("CUSTOM", fromDate, toDate);
        }

        String normalizedPeriod = period == null || period.trim().isEmpty()
                ? "MONTH"
                : period.trim().toUpperCase();

        LocalDate resolvedFromDate;

        switch (normalizedPeriod) {
            case "TODAY":
                resolvedFromDate = today;
                break;

            case "WEEK":
                resolvedFromDate = today.minusDays(6);
                break;

            case "MONTH":
                resolvedFromDate = today.withDayOfMonth(1);
                break;

            case "YEAR":
                resolvedFromDate = today.withDayOfYear(1);
                break;

            default:
                throw new IllegalArgumentException(
                        "Invalid period. Allowed values are TODAY, WEEK, MONTH, YEAR"
                );
        }

        return new DateRange(normalizedPeriod, resolvedFromDate, today);
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate cannot be after toDate");
        }
    }

    private record DateRange(
            String period,
            LocalDate fromDate,
            LocalDate toDate
    ) {
    }


    @Override
    @Transactional(readOnly = true)
    public TopConvertedLeadsResponseDto getTopConvertedLeads(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required and must be positive");
        }

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "User not found with ID: " + userId,
                    "USER_NOT_FOUND"
            );
        }

        DateRange dateRange = resolveDateRange(period, fromDate, toDate);
        int safeLimit = resolveLimit(limit);

        Pageable pageable = PageRequest.of(0, safeLimit);

        List<TopConvertedLeadItemDto> items =
                invoiceRepository.findTopConvertedLeadsForSalesperson(
                        userId,
                        InvoiceStatus.GENERATED,
                        dateRange.fromDate(),
                        dateRange.toDate(),
                        pageable
                );

        return TopConvertedLeadsResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .limit(safeLimit)
                .topConvertedLeads(items)
                .build();
    }




}