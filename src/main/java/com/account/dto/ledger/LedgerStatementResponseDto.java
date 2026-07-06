package com.account.dto.ledger;

import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerStatementResponseDto {

    private Long ledgerId;
    private String ledgerName;
    private String ledgerCode;
    private LedgerType ledgerType;

    private LocalDate fromDate;
    private LocalDate toDate;

    private BigDecimal openingBalanceAmount;
    private DebitCredit openingBalanceType;

    private BigDecimal closingBalanceAmount;
    private DebitCredit closingBalanceType;

    private BigDecimal totalDebit;
    private BigDecimal totalCredit;

    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    private List<LedgerTransactionResponseDto> transactions;
}