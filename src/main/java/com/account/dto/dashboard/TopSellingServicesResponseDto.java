package com.account.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class TopSellingServicesResponseDto {

    private Long userId;
    private String period;
    private LocalDate fromDate;
    private LocalDate toDate;
    private Integer limit;

    private List<TopSellingServiceItemDto> topSellingServices;
}