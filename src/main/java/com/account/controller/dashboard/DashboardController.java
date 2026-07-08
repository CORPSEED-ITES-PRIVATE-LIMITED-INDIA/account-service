package com.account.controller.dashboard;

import com.account.dto.dashboard.*;
import com.account.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Invoice Dashboard", description = "Dashboard APIs based on tax invoices")
@RestController
@RequestMapping("/accountService/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/top-selling-services")
    @Operation(summary = "Get top selling services/solutions from generated invoices")
    public ResponseEntity<TopSellingServicesResponseDto> getTopSellingServices(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "MONTH")
            String period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false, defaultValue = "3")
            Integer limit
    ) {
        TopSellingServicesResponseDto response =
                dashboardService.getTopSellingServices(
                        userId,
                        period,
                        fromDate,
                        toDate,
                        limit
                );

        //

        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-converted-leads")
    @Operation(summary = "Get top converted leads by highest invoice value")
    public ResponseEntity<TopConvertedLeadsResponseDto> getTopConvertedLeads(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "MONTH")
            String period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false, defaultValue = "5")
            Integer limit
    ) {
        TopConvertedLeadsResponseDto response =
                dashboardService.getTopConvertedLeads(
                        userId,
                        period,
                        fromDate,
                        toDate,
                        limit
                );

        return ResponseEntity.ok(response);
    }




    @GetMapping("/revenue-cards")
    @Operation(summary = "Get revenue and revenue pipeline cards")
    public ResponseEntity<RevenueCardsResponseDto> getRevenueCards(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "MONTH")
            String period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        RevenueCardsResponseDto response =
                dashboardService.getRevenueCards(
                        userId,
                        period,
                        fromDate,
                        toDate
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/revenue-trend")
    @Operation(summary = "Get monthly revenue trend from generated invoices")
    public ResponseEntity<RevenueTrendResponseDto> getRevenueTrend(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "6")
            Integer months
    ) {
        RevenueTrendResponseDto response =
                dashboardService.getRevenueTrend(userId, months);

        return ResponseEntity.ok(response);
    }


    @GetMapping("/revenue-by-service")
    @Operation(summary = "Get revenue by service/solution from generated invoices")
    public ResponseEntity<RevenueByServiceResponseDto> getRevenueByService(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "MONTH")
            String period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false, defaultValue = "5")
            Integer limit
    ) {
        RevenueByServiceResponseDto response =
                dashboardService.getRevenueByService(
                        userId,
                        period,
                        fromDate,
                        toDate,
                        limit
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/top-companies")
    @Operation(summary = "Get top companies by generated invoice revenue")
    public ResponseEntity<TopCompaniesResponseDto> getTopCompanies(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "MONTH")
            String period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false, defaultValue = "5")
            Integer limit
    ) {
        TopCompaniesResponseDto response =
                dashboardService.getTopCompanies(
                        userId,
                        period,
                        fromDate,
                        toDate,
                        limit
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/payment-summary")
    @Operation(summary = "Get payment summary for dashboard")
    public ResponseEntity<PaymentSummaryResponseDto> getPaymentSummary(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "MONTH")
            String period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        PaymentSummaryResponseDto response =
                dashboardService.getPaymentSummary(
                        userId,
                        period,
                        fromDate,
                        toDate
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/billing-overview")
    @Operation(summary = "Get billing, payment received, outstanding and pending approval overview")
    public ResponseEntity<BillingOverviewResponseDto> getBillingOverview(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "MONTH")
            String period,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        BillingOverviewResponseDto response =
                dashboardService.getBillingOverview(
                        userId,
                        period,
                        fromDate,
                        toDate
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/billing-vs-collection")
    @Operation(summary = "Get monthly billing vs collection trend")
    public ResponseEntity<BillingCollectionTrendResponseDto> getBillingVsCollectionTrend(
            @RequestParam Long userId,

            @RequestParam(required = false, defaultValue = "6")
            Integer months,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate
    ) {
        BillingCollectionTrendResponseDto response =
                dashboardService.getBillingVsCollectionTrend(
                        userId,
                        months,
                        fromDate,
                        toDate
                );

        return ResponseEntity.ok(response);
    }






}