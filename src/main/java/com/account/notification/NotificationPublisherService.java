package com.account.notification;

import com.account.feignClient.NotificationClient;
import com.account.notification.dto.NotificationCreateRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPublisherService {

    private final NotificationClient notificationClient;

    public void sendNotification(NotificationCreateRequestDto requestDto) {
        try {
            if (requestDto == null || requestDto.getReceiverId() == null) {
                log.warn("Notification skipped because receiverId is missing");
                return;
            }

            log.info(
                    "Sending notification to notification-service | receiverId={}, module={}, eventType={}, referenceId={}",
                    requestDto.getReceiverId(),
                    requestDto.getModule(),
                    requestDto.getEventType(),
                    requestDto.getReferenceId()
            );

            notificationClient.createNotification(requestDto);

            log.info(
                    "Notification sent successfully | receiverId={}, eventType={}, referenceId={}",
                    requestDto.getReceiverId(),
                    requestDto.getEventType(),
                    requestDto.getReferenceId()
            );

        } catch (Exception e) {
            /*
             * Payment registration / approval should not fail
             * only because notification-service is down.
             */
            log.error("Failed to send notification: {}", e.getMessage());
        }
    }
}