package com.account.dto.estimate.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EstimateStatusResponseDto {
    private Long estimateId;
    private String estimateNumber;
    private String status;
    private String message;
}