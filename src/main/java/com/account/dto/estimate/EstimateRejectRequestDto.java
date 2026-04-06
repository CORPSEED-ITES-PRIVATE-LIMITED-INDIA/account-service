package com.account.dto.estimate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstimateRejectRequestDto {

    @NotBlank(message = "Rejection reason is required")
    private String rejectionReason;

    @NotNull(message = "Rejected by user id is required")
    private Long rejectedByUserId;
}
