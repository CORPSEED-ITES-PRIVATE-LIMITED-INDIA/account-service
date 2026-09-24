package com.account.dto.dashboard;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class ApprovalQueueResponseDto {

    private Long userId;
    private String period;
    private LocalDate fromDate;
    private LocalDate toDate;

    private Integer limit;

    private Long totalPendingApprovals;
    private Long urgentCount;

    private List<ApprovalQueueItemDto> items;
}