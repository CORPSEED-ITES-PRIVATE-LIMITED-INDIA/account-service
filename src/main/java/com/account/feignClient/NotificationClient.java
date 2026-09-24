package com.account.feignClient;

import com.account.notification.dto.NotificationCreateRequestDto;
import com.account.notification.dto.NotificationResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "notification-service")
public interface NotificationClient {

    @PostMapping("/api/notifications")
    NotificationResponseDto createNotification(
            @RequestBody NotificationCreateRequestDto requestDto
    );
}