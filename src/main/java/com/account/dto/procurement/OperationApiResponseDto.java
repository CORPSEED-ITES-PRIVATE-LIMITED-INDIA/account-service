package com.account.dto.procurement;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationApiResponseDto<T> {

    private Boolean success;
    private String message;
    private Integer statusCode;
    private T data;
    private LocalDateTime timestamp;
}
