package com.account.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class RecentPaymentsResponseDto {

    private Long userId;
    private String period;

    private LocalDate fromDate;
    private LocalDate toDate;

    private Integer page;
    private Integer size;

    private Long totalElements;
    private Integer totalPages;

    private List<RecentPaymentItemDto> recentPayments;
}