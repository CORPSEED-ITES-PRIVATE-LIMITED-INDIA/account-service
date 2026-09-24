package com.account.dto.estimate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EstimateCancelRequestDto {

    @NotBlank(message = "Cancellation reason is required")
    private String cancellationReason;

    @NotNull(message = "Cancelled by user id is required")
    private Long cancelledByUserId;

}