package com.account.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PaymentLegalSummaryResponseDto {
    private long totalPending;
    private List<PaymentLegalStatusCountDto> statusCounts;
}