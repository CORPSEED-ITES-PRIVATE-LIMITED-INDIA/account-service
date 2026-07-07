package com.account.serviceImpl;

import com.account.domain.status.InvoiceStatus;
import com.account.domain.status.UnbilledStatus;
import com.account.dto.dashboard.*;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.InvoiceRepository;
import com.account.repository.UnbilledInvoiceRepository;
import com.account.repository.UserRepository;
import com.account.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;

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


    @Override
    @Transactional(readOnly = true)
    public RevenueCardsResponseDto getRevenueCards(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validateUser(userId);

        DateRange currentRange = resolveDateRange(period, fromDate, toDate);
        DateRange previousRange = resolvePreviousDateRange(currentRange);

        BigDecimal currentRevenue = safeMoney(
                invoiceRepository.sumGeneratedRevenueForSalesperson(
                        userId,
                        InvoiceStatus.GENERATED,
                        currentRange.fromDate(),
                        currentRange.toDate()
                )
        );

        BigDecimal previousRevenue = safeMoney(
                invoiceRepository.sumGeneratedRevenueForSalesperson(
                        userId,
                        InvoiceStatus.GENERATED,
                        previousRange.fromDate(),
                        previousRange.toDate()
                )
        );

        BigDecimal growthPercentage = calculateGrowthPercentage(currentRevenue, previousRevenue);

        RevenueCardDto revenueCard = new RevenueCardDto(
                currentRevenue,
                growthPercentage,
                resolveGrowthDirection(growthPercentage),
                buildComparisonLabel(currentRange.period(), previousRange)
        );
        revenueCard.normalize();

        LocalDateTime pipelineFrom = currentRange.fromDate().atStartOfDay();
        LocalDateTime pipelineTo = currentRange.toDate().plusDays(1).atStartOfDay();

        List<UnbilledStatus> pipelineStatuses = Arrays.asList(
                UnbilledStatus.PENDING_APPROVAL,
                UnbilledStatus.APPROVED
        );

        BigDecimal pipelineAmount = safeMoney(
                unbilledInvoiceRepository.sumRevenuePipelineForSalesperson(
                        userId,
                        pipelineStatuses,
                        pipelineFrom,
                        pipelineTo,
                        BigDecimal.ZERO
                )
        );

        Long pipelineDealCount =
                unbilledInvoiceRepository.countRevenuePipelineDealsForSalesperson(
                        userId,
                        pipelineStatuses,
                        pipelineFrom,
                        pipelineTo,
                        BigDecimal.ZERO
                );

        RevenuePipelineCardDto pipelineCard = new RevenuePipelineCardDto(
                pipelineAmount,
                pipelineDealCount,
                null
        );
        pipelineCard.normalize();

        return RevenueCardsResponseDto.builder()
                .userId(userId)
                .period(currentRange.period())
                .fromDate(currentRange.fromDate())
                .toDate(currentRange.toDate())
                .revenue(revenueCard)
                .revenuePipeline(pipelineCard)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueTrendResponseDto getRevenueTrend(Long userId, Integer months) {
        validateUser(userId);

        int safeMonths = months == null || months <= 0 ? 6 : Math.min(months, 24);

        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(safeMonths - 1L);

        LocalDate fromDate = startMonth.atDay(1);
        LocalDate toDate = LocalDate.now();

        List<Object[]> rows =
                invoiceRepository.findRevenueTrendRowsForSalesperson(
                        userId,
                        InvoiceStatus.GENERATED,
                        fromDate,
                        toDate
                );

        Map<YearMonth, BigDecimal> revenueByMonth = new LinkedHashMap<>();

        for (int i = 0; i < safeMonths; i++) {
            YearMonth ym = startMonth.plusMonths(i);
            revenueByMonth.put(ym, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        for (Object[] row : rows) {
            LocalDate invoiceDate = (LocalDate) row[0];
            BigDecimal invoiceAmount = safeMoney((BigDecimal) row[1]);

            if (invoiceDate == null) {
                continue;
            }

            YearMonth ym = YearMonth.from(invoiceDate);

            if (revenueByMonth.containsKey(ym)) {
                revenueByMonth.put(
                        ym,
                        safeMoney(revenueByMonth.get(ym).add(invoiceAmount))
                );
            }
        }

        DateTimeFormatter labelFormatter =
                DateTimeFormatter.ofPattern("MMM ''yy", Locale.ENGLISH);

        List<RevenueTrendPointDto> points = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Map.Entry<YearMonth, BigDecimal> entry : revenueByMonth.entrySet()) {
            YearMonth ym = entry.getKey();
            BigDecimal revenue = safeMoney(entry.getValue());

            RevenueTrendPointDto point = new RevenueTrendPointDto(
                    ym.toString(),
                    ym.format(labelFormatter),
                    revenue
            );
            point.normalize();

            points.add(point);
            totalRevenue = totalRevenue.add(revenue);
        }

        return RevenueTrendResponseDto.builder()
                .userId(userId)
                .groupBy("MONTHLY")
                .fromDate(fromDate)
                .toDate(toDate)
                .totalRevenue(safeMoney(totalRevenue))
                .points(points)
                .build();
    }

    private void validateUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId is required and must be positive");
        }

        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "User not found with ID: " + userId,
                    "USER_NOT_FOUND"
            );
        }
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateGrowthPercentage(BigDecimal current, BigDecimal previous) {
        BigDecimal currentValue = safeMoney(current);
        BigDecimal previousValue = safeMoney(previous);

        if (previousValue.compareTo(BigDecimal.ZERO) == 0) {
            return currentValue.compareTo(BigDecimal.ZERO) > 0
                    ? new BigDecimal("100.00")
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return currentValue
                .subtract(previousValue)
                .multiply(new BigDecimal("100"))
                .divide(previousValue, 2, RoundingMode.HALF_UP);
    }

    private String resolveGrowthDirection(BigDecimal growthPercentage) {
        if (growthPercentage == null || growthPercentage.compareTo(BigDecimal.ZERO) == 0) {
            return "SAME";
        }

        return growthPercentage.compareTo(BigDecimal.ZERO) > 0 ? "UP" : "DOWN";
    }

    private DateRange resolvePreviousDateRange(DateRange currentRange) {
        String period = currentRange.period();

        if ("TODAY".equals(period)) {
            return new DateRange(
                    "PREVIOUS_TODAY",
                    currentRange.fromDate().minusDays(1),
                    currentRange.toDate().minusDays(1)
            );
        }

        if ("WEEK".equals(period)) {
            return new DateRange(
                    "PREVIOUS_WEEK",
                    currentRange.fromDate().minusWeeks(1),
                    currentRange.toDate().minusWeeks(1)
            );
        }

        if ("MONTH".equals(period)) {
            return new DateRange(
                    "PREVIOUS_MONTH",
                    currentRange.fromDate().minusMonths(1),
                    currentRange.toDate().minusMonths(1)
            );
        }

        if ("YEAR".equals(period)) {
            return new DateRange(
                    "PREVIOUS_YEAR",
                    currentRange.fromDate().minusYears(1),
                    currentRange.toDate().minusYears(1)
            );
        }

        long days = ChronoUnit.DAYS.between(
                currentRange.fromDate(),
                currentRange.toDate()
        ) + 1;

        LocalDate previousToDate = currentRange.fromDate().minusDays(1);
        LocalDate previousFromDate = previousToDate.minusDays(days - 1);

        return new DateRange("PREVIOUS_CUSTOM", previousFromDate, previousToDate);
    }

    private String buildComparisonLabel(String currentPeriod, DateRange previousRange) {
        if ("TODAY".equals(currentPeriod)) {
            return "vs yesterday";
        }

        if ("WEEK".equals(currentPeriod)) {
            return "vs last week";
        }

        if ("MONTH".equals(currentPeriod)) {
            return "vs " + previousRange.fromDate()
                    .format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH));
        }

        if ("YEAR".equals(currentPeriod)) {
            return "vs " + previousRange.fromDate().getYear();
        }

        return "vs previous period";
    }


    @Override
    @Transactional(readOnly = true)
    public RevenueByServiceResponseDto getRevenueByService(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    ) {
        validateUser(userId);

        DateRange dateRange = resolveDateRange(period, fromDate, toDate);
        int safeLimit = resolveLimit(limit);

        Pageable pageable = PageRequest.of(0, safeLimit);

        List<RevenueByServiceItemDto> items =
                invoiceRepository.findRevenueByServiceForSalesperson(
                        userId,
                        InvoiceStatus.GENERATED,
                        dateRange.fromDate(),
                        dateRange.toDate(),
                        pageable
                );

        BigDecimal totalRevenue = items.stream()
                .map(item -> safeMoney(item.getRevenue()))
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        BigDecimal maxRevenue = items.stream()
                .map(item -> safeMoney(item.getRevenue()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        for (RevenueByServiceItemDto item : items) {
            BigDecimal revenue = safeMoney(item.getRevenue());

            BigDecimal percentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (maxRevenue.compareTo(BigDecimal.ZERO) > 0) {
                percentage = revenue
                        .multiply(new BigDecimal("100"))
                        .divide(maxRevenue, 2, RoundingMode.HALF_UP);
            }

            item.setRevenue(revenue);
            item.setPercentage(percentage);
        }

        return RevenueByServiceResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .limit(safeLimit)
                .totalRevenue(safeMoney(totalRevenue))
                .revenueByServices(items)
                .build();
    }



}