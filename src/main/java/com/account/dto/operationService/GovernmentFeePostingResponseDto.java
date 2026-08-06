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

    /** Entry A. Null for a company-funded expense. */
    private Long receiptVoucherId;
    private String receiptVoucherNumber;

    /** Entry B, or the company-funded accrual journal. */
    private Long journalVoucherId;
    private String journalVoucherNumber;

    private Long receivingBankLedgerId;
    private Long clientAdvanceLedgerId;
    private Long governmentFeeExpenseLedgerId;
    private Long governmentFeePayableLedgerId;

    /** Backward-compatible alias of journalVoucherId. */
    @Deprecated
    private Long voucherId;

    /** Backward-compatible alias of journalVoucherNumber. */
    @Deprecated
    private String voucherNumber;

    private LocalDateTime postedAt;
}
