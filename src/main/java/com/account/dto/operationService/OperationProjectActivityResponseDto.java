package com.account.dto.operationService;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationProjectActivityResponseDto {

    private Long activityId;

    private ActivityType activityType;

    private String title;

    private String summary;

    private LocalDateTime activityDate;

    private Long createdByUserId;

    private String createdByUserName;

    private Object details;
}
