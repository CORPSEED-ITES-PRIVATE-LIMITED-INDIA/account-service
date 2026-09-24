package com.account.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDto {

    private Long id;

    private Long receiverId;

    private Long actorId;

    private String actorName;

    private NotificationCreateRequestDto.NotificationModule module;

    private NotificationCreateRequestDto.NotificationEventType eventType;

    private Long referenceId;

    private String referenceNumber;

    private String title;

    private String message;

    private String redirectUrl;

    private NotificationPriority priority;

    private NotificationCreateRequestDto.NotificationDisplayType displayType;

    private boolean read;

    private LocalDateTime readAt;

    private boolean deleted;

    private String metadataJson;

    private LocalDateTime createdAt;
}