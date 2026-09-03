package com.account.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentLegalStatusCountDto {
    private String status;
    private long count;
}