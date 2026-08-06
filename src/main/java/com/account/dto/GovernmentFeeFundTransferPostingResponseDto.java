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
public class GovernmentFeeFundTransferPostingResponseDto {
    private String postingStatus;
    private String message;
    private Long operationExpenseId;
    private Long contraVoucherId;
    private String contraVoucherNumber;
    private Long fromBankLedgerId;
    private Long toBankLedgerId;
    private LocalDateTime postedAt;
}
