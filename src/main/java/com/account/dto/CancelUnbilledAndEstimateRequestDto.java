package com.account.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelUnbilledAndEstimateRequestDto {
    @NotNull
    private Long cancelledByUserId;
    private String reason;
}