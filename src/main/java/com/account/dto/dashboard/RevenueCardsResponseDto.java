package com.account.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class RevenueCardsResponseDto {

    private Long userId;
    private String period;
    private LocalDate fromDate;
    private LocalDate toDate;

    private RevenueCardDto revenue;
    private RevenuePipelineCardDto revenuePipeline;
}