package com.account.dto.payment;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewPaymentLegalVerificationRequestDto {

    @NotNull(message = "approve is required")
    private Boolean approve;

    private String remark;
}