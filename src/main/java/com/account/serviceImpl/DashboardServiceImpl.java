package com.account.serviceImpl;

import com.account.domain.status.InvoiceStatus;
import com.account.domain.status.PaymentStatus;
import com.account.domain.status.UnbilledStatus;
import com.account.dto.dashboard.*;
import com.account.exception.ResourceNotFoundException;
import com.account.repository.InvoiceRepository;
import com.account.repository.PaymentReceiptRepository;
import com.account.repository.UnbilledInvoiceRepository;
import com.account.repository.UserRepository;
import com.account.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final UnbilledInvoiceRepository unbilledInvoiceRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;

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

    @Override
    @Transactional(readOnly = true)
    public TopCompaniesResponseDto getTopCompanies(
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

        List<TopCompanyItemDto> items =
                invoiceRepository.findTopCompaniesForSalesperson(
                        userId,
                        InvoiceStatus.GENERATED,
                        dateRange.fromDate(),
                        dateRange.toDate(),
                        pageable
                );

        items.forEach(item -> item.setTotalRevenue(safeMoney(item.getTotalRevenue())));

        return TopCompaniesResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .limit(safeLimit)
                .topCompanies(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponseDto getPaymentSummary(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validateUser(userId);

        DateRange dateRange = resolveDateRange(period, fromDate, toDate);

        LocalDateTime fromDateTime = dateRange.fromDate().atStartOfDay();
        LocalDateTime toDateTime = dateRange.toDate().plusDays(1).atStartOfDay();

        BigDecimal totalBilled = safeMoney(
                unbilledInvoiceRepository.sumTotalBilledForSalesperson(
                        userId,
                        fromDateTime,
                        toDateTime
                )
        );

        BigDecimal received = safeMoney(
                unbilledInvoiceRepository.sumReceivedForSalesperson(
                        userId,
                        fromDateTime,
                        toDateTime
                )
        );

        BigDecimal pending = safeMoney(
                unbilledInvoiceRepository.sumPendingForSalesperson(
                        userId,
                        fromDateTime,
                        toDateTime
                )
        );

        BigDecimal collectionPercentage = calculateCollectionPercentage(
                received,
                totalBilled
        );

        return PaymentSummaryResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .totalBilled(totalBilled)
                .received(received)
                .pending(pending)
                .collectionPercentage(collectionPercentage)
                .build();
    }



    private BigDecimal calculateCollectionPercentage(
            BigDecimal received,
            BigDecimal totalBilled
    ) {
        BigDecimal safeReceived = safeMoney(received);
        BigDecimal safeTotalBilled = safeMoney(totalBilled);

        if (safeTotalBilled.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return safeReceived
                .multiply(new BigDecimal("100"))
                .divide(safeTotalBilled, 2, RoundingMode.HALF_UP);
    }
    @Override
    @Transactional(readOnly = true)
    public BillingCollectionTrendResponseDto getBillingVsCollectionTrend(
            Long userId,
            Integer months,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validateUser(userId);

        LocalDate today = LocalDate.now();

        LocalDate resolvedFromDate;
        LocalDate resolvedToDate;

        if (fromDate != null || toDate != null) {
            if (fromDate == null || toDate == null) {
                throw new IllegalArgumentException("Both fromDate and toDate are required");
            }

            if (fromDate.isAfter(toDate)) {
                throw new IllegalArgumentException("fromDate cannot be after toDate");
            }

            resolvedFromDate = fromDate.withDayOfMonth(1);
            resolvedToDate = toDate;
        } else {
            int safeMonths = months == null || months <= 0 ? 6 : Math.min(months, 24);

            YearMonth currentMonth = YearMonth.now();
            YearMonth startMonth = currentMonth.minusMonths(safeMonths - 1L);

            resolvedFromDate = startMonth.atDay(1);
            resolvedToDate = today;
        }

        LocalDateTime fromDateTime = resolvedFromDate.atStartOfDay();
        LocalDateTime toDateTime = resolvedToDate.plusDays(1).atStartOfDay();

        List<MonthlyAmountProjection> billedRows =
                unbilledInvoiceRepository.findMonthlyBilledAmountForSalesperson(
                        userId,
                        fromDateTime,
                        toDateTime
                );

        List<MonthlyAmountProjection> collectedRows =
                paymentReceiptRepository.findMonthlyCollectionAmountForSalesperson(
                        userId,
                        resolvedFromDate,
                        resolvedToDate
                );

        Map<String, BigDecimal> billedMap = new HashMap<>();
        Map<String, BigDecimal> collectedMap = new HashMap<>();

        for (MonthlyAmountProjection row : billedRows) {
            billedMap.put(row.getMonthKey(), safeMoney(row.getAmount()));
        }

        for (MonthlyAmountProjection row : collectedRows) {
            collectedMap.put(row.getMonthKey(), safeMoney(row.getAmount()));
        }

        YearMonth startMonth = YearMonth.from(resolvedFromDate);
        YearMonth endMonth = YearMonth.from(resolvedToDate);

        List<BillingCollectionTrendPointDto> points = new ArrayList<>();

        BigDecimal totalBilled = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCollected = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        YearMonth runningMonth = startMonth;

        while (!runningMonth.isAfter(endMonth)) {
            String monthKey = runningMonth.toString();

            BigDecimal billed = safeMoney(billedMap.get(monthKey));
            BigDecimal collected = safeMoney(collectedMap.get(monthKey));

            String label = runningMonth
                    .getMonth()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            points.add(new BillingCollectionTrendPointDto(
                    monthKey,
                    label,
                    billed,
                    collected
            ));

            totalBilled = totalBilled.add(billed);
            totalCollected = totalCollected.add(collected);

            runningMonth = runningMonth.plusMonths(1);
        }

        return BillingCollectionTrendResponseDto.builder()
                .userId(userId)
                .groupBy("MONTHLY")
                .fromDate(resolvedFromDate)
                .toDate(resolvedToDate)
                .totalBilled(safeMoney(totalBilled))
                .totalCollected(safeMoney(totalCollected))
                .points(points)
                .build();
    }



    @Override
    @Transactional(readOnly = true)
    public BillingOverviewResponseDto getBillingOverview(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validateUser(userId);

        DateRange currentRange = resolveDateRange(period, fromDate, toDate);
        DateRange previousRange = resolvePreviousDateRange(currentRange);

        LocalDateTime currentFrom = currentRange.fromDate().atStartOfDay();
        LocalDateTime currentTo = currentRange.toDate().plusDays(1).atStartOfDay();

        LocalDateTime previousFrom = previousRange.fromDate().atStartOfDay();
        LocalDateTime previousTo = previousRange.toDate().plusDays(1).atStartOfDay();

        BigDecimal currentTotalBilled = safeMoney(
                unbilledInvoiceRepository.sumTotalBilledByUserAndDateRange(
                        userId,
                        currentFrom,
                        currentTo
                )
        );

        BigDecimal previousTotalBilled = safeMoney(
                unbilledInvoiceRepository.sumTotalBilledByUserAndDateRange(
                        userId,
                        previousFrom,
                        previousTo
                )
        );

        BigDecimal currentReceived = safeMoney(
                invoiceRepository.sumInvoiceReceivedByUserAndDateRange(
                        userId,
                        InvoiceStatus.GENERATED,
                        currentRange.fromDate(),
                        currentRange.toDate()
                )
        );

        BigDecimal previousReceived = safeMoney(
                invoiceRepository.sumInvoiceReceivedByUserAndDateRange(
                        userId,
                        InvoiceStatus.GENERATED,
                        previousRange.fromDate(),
                        previousRange.toDate()
                )
        );

        BigDecimal currentOutstanding = safeMoney(
                unbilledInvoiceRepository.sumOutstandingByUserAndDateRange(
                        userId,
                        currentFrom,
                        currentTo
                )
        );

        BigDecimal previousOutstanding = safeMoney(
                unbilledInvoiceRepository.sumOutstandingByUserAndDateRange(
                        userId,
                        previousFrom,
                        previousTo
                )
        );

        Long pendingApprovalCount =
                unbilledInvoiceRepository.countPendingApprovalsByUserAndDateRange(
                        userId,
                        UnbilledStatus.PENDING_APPROVAL,
                        currentFrom,
                        currentTo
                );

        LocalDate today = LocalDate.now();

        Long urgentTodayCount =
                unbilledInvoiceRepository.countPendingApprovalsTodayByUser(
                        userId,
                        UnbilledStatus.PENDING_APPROVAL,
                        today.atStartOfDay(),
                        today.plusDays(1).atStartOfDay()
                );

        BillingOverviewCardDto totalBilledCard = buildMoneyCard(
                currentTotalBilled,
                previousTotalBilled,
                currentRange,
                false
        );

        BillingOverviewCardDto receivedCard = buildMoneyCard(
                currentReceived,
                previousReceived,
                currentRange,
                false
        );

        /*
         * For outstanding, lower value is better.
         * So if outstanding decreases, growthDirection should be DOWN.
         */
        BillingOverviewCardDto outstandingCard = buildMoneyCard(
                currentOutstanding,
                previousOutstanding,
                currentRange,
                true
        );

        PendingApprovalCardDto pendingApprovalCard = new PendingApprovalCardDto(
                pendingApprovalCount,
                urgentTodayCount,
                null
        );
        pendingApprovalCard.normalize();

        return BillingOverviewResponseDto.builder()
                .userId(userId)
                .period(currentRange.period())
                .fromDate(currentRange.fromDate())
                .toDate(currentRange.toDate())
                .totalBilled(totalBilledCard)
                .paymentReceived(receivedCard)
                .outstanding(outstandingCard)
                .pendingApprovals(pendingApprovalCard)
                .build();
    }

    private BillingOverviewCardDto buildMoneyCard(
            BigDecimal currentValue,
            BigDecimal previousValue,
            DateRange currentRange,
            boolean lowerIsBetter
    ) {
        BigDecimal growth = calculateGrowthPercentage(currentValue, previousValue);

        String direction;

        if (growth.compareTo(BigDecimal.ZERO) == 0) {
            direction = "SAME";
        } else if (growth.compareTo(BigDecimal.ZERO) > 0) {
            direction = lowerIsBetter ? "DOWN_BAD" : "UP";
        } else {
            direction = lowerIsBetter ? "DOWN" : "DOWN_BAD";
        }

        BillingOverviewCardDto card = new BillingOverviewCardDto(
                safeMoney(currentValue),
                growth.abs().setScale(2, RoundingMode.HALF_UP),
                direction,
                buildComparisonLabel(currentRange.period())
        );

        card.normalize();
        return card;
    }




    private String buildComparisonLabel(String currentPeriod) {
        if ("TODAY".equals(currentPeriod)) {
            return "vs yesterday";
        }

        if ("WEEK".equals(currentPeriod)) {
            return "vs last week";
        }

        if ("MONTH".equals(currentPeriod)) {
            return "vs last month";
        }

        if ("YEAR".equals(currentPeriod)) {
            return "vs last year";
        }

        return "vs previous period";
    }


    @Override
    @Transactional(readOnly = true)
    public InvoiceStatusOverviewResponseDto getInvoiceStatusOverview(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        validateUser(userId);

        DateRange dateRange = resolveDateRange(period, fromDate, toDate);

        List<InvoiceStatusCountProjection> rows =
                invoiceRepository.findInvoiceStatusOverviewForSalesperson(
                        userId,
                        dateRange.fromDate(),
                        dateRange.toDate()
                );

        Map<String, Long> countMap = new HashMap<>();

        countMap.put("GENERATED", 0L);
        countMap.put("PAID", 0L);
        countMap.put("PARTIALLY_PAID", 0L);
        countMap.put("OVERDUE", 0L);

        for (InvoiceStatusCountProjection row : rows) {
            if (row.getStatus() != null) {
                countMap.put(row.getStatus(), row.getCount() == null ? 0L : row.getCount());
            }
        }

        long totalInvoices = countMap.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        List<InvoiceStatusOverviewItemDto> statuses = new ArrayList<>();

        statuses.add(new InvoiceStatusOverviewItemDto(
                "Generated",
                countMap.get("GENERATED"),
                calculateStatusPercentage(countMap.get("GENERATED"), totalInvoices)
        ));

        statuses.add(new InvoiceStatusOverviewItemDto(
                "Paid",
                countMap.get("PAID"),
                calculateStatusPercentage(countMap.get("PAID"), totalInvoices)
        ));

        statuses.add(new InvoiceStatusOverviewItemDto(
                "Partially Paid",
                countMap.get("PARTIALLY_PAID"),
                calculateStatusPercentage(countMap.get("PARTIALLY_PAID"), totalInvoices)
        ));

        statuses.add(new InvoiceStatusOverviewItemDto(
                "Overdue",
                countMap.get("OVERDUE"),
                calculateStatusPercentage(countMap.get("OVERDUE"), totalInvoices)
        ));

        return InvoiceStatusOverviewResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .totalInvoices(totalInvoices)
                .statuses(statuses)
                .build();
    }

    private Double calculateStatusPercentage(Long count, Long total) {
        if (count == null || total == null || total == 0) {
            return 0.0;
        }

        return BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)

                .doubleValue();
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalQueueResponseDto getApprovalQueue(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            Integer limit
    ) {
        validateUser(userId);

        DateRange dateRange = resolveDateRange(period, fromDate, toDate);

        LocalDateTime fromDateTime = dateRange.fromDate().atStartOfDay();
        LocalDateTime toDateTime = dateRange.toDate().plusDays(1).atStartOfDay();

        int safeLimit = limit == null || limit <= 0 ? 4 : Math.min(limit, 50);

        Pageable pageable = PageRequest.of(0, safeLimit);

        Page<Object[]> page =
                unbilledInvoiceRepository.findApprovalQueueForDashboard(
                        userId,
                        fromDateTime,
                        toDateTime,
                        pageable
                );

        List<ApprovalQueueItemDto> items = page.getContent()
                .stream()
                .map(this::mapApprovalQueueRow)
                .toList();

        Long urgentCount =
                unbilledInvoiceRepository.countUrgentApprovalQueueForDashboard(
                        userId,
                        fromDateTime,
                        toDateTime
                );

        return ApprovalQueueResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .limit(safeLimit)
                .totalPendingApprovals(page.getTotalElements())
                .urgentCount(urgentCount == null ? 0L : urgentCount)
                .items(items)
                .build();
    }

    private ApprovalQueueItemDto mapApprovalQueueRow(Object[] row) {
        return new ApprovalQueueItemDto(
                toLong(row[0]),              // itemId
                toStringValue(row[1]),       // itemType
                toStringValue(row[2]),       // title
                toStringValue(row[3]),       // subTitle
                toLong(row[4]),              // companyId
                toStringValue(row[5]),       // companyName
                toLong(row[6]),              // referenceId
                toStringValue(row[7]),       // referenceNumber
                toBigDecimal(row[8]),        // amount
                toStringValue(row[9]),       // sourceStatus
                toStringValue(row[10]),      // badge
                toStringValue(row[11]),      // priority
                toLocalDateTime(row[12])     // createdAt
        );
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        return Long.valueOf(value.toString());
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.setScale(2, RoundingMode.HALF_UP);
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }

        return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }

        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        return LocalDateTime.parse(value.toString());
    }


    @Override
    @Transactional(readOnly = true)
    public RecentPaymentsResponseDto getRecentPayments(
            Long userId,
            String period,
            LocalDate fromDate,
            LocalDate toDate,
            String status,
            Integer page,
            Integer size
    ) {
        validateUser(userId);

        DateRange dateRange = resolveDateRange(period, fromDate, toDate);

        int safePage = page == null || page <= 0 ? 0 : page - 1;
        int safeSize = size == null || size <= 0 ? 4 : Math.min(size, 100);

        PaymentStatus paymentStatus = resolvePaymentStatus(status);

        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<RecentPaymentItemDto> resultPage =
                paymentReceiptRepository.findRecentPaymentsForDashboard(
                        userId,
                        dateRange.fromDate(),
                        dateRange.toDate(),
                        paymentStatus,
                        pageable
                );

        resultPage.getContent().forEach(item ->
                item.setDisplayStatus(resolvePaymentDisplayStatus(item.getPaymentStatus()))
        );

        return RecentPaymentsResponseDto.builder()
                .userId(userId)
                .period(dateRange.period())
                .fromDate(dateRange.fromDate())
                .toDate(dateRange.toDate())
                .page(safePage + 1)
                .size(safeSize)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .recentPayments(resultPage.getContent())
                .build();
    }

    private PaymentStatus resolvePaymentStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }

        try {
            return PaymentStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid payment status. Allowed values are PENDING, APPROVED, REJECTED"
            );
        }
    }

    private String resolvePaymentDisplayStatus(PaymentStatus status) {
        if (status == null) {
            return "Unknown";
        }

        return switch (status) {
            case APPROVED -> "Received";
            case PENDING -> "Clearing";
            case REJECTED -> "Rejected";
        };
    }


}