package com.account.dto.ledger;

import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionResponseDto {

    private Long entryId;

    private Long voucherId;
    private String voucherNumber;
    private VoucherType voucherType;
    private LocalDate voucherDate;

    private VoucherSourceType sourceType;
    private Long sourceId;
    private VoucherStatus status;

    private Long ledgerId;
    private String ledgerName;
    private String ledgerCode;

    private BigDecimal debitAmount;
    private BigDecimal creditAmount;

    private BigDecimal runningBalanceAmount;
    private DebitCredit runningBalanceType;

    private String narration;
}