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
public class GovernmentFeePostingResponseDto {

    private String postingStatus;

    private String message;

    private Long operationExpenseId;

    private Long voucherId;

    private String voucherNumber;

    private Long governmentFeeExpenseLedgerId;

    private Long governmentFeePayableLedgerId;

    private LocalDateTime postedAt;
}