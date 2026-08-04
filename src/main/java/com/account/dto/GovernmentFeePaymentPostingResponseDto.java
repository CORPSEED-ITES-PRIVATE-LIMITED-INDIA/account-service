package com.account.dto.operationService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentFeePaymentPostingResponseDto {
    private String postingStatus;
    private String message;
    private Long operationExpenseId;
    private Long paymentVoucherId;
    private String paymentVoucherNumber;
    private Long governmentFeePayableLedgerId;
    private Long paymentBankLedgerId;
    private LocalDateTime postedAt;
}
