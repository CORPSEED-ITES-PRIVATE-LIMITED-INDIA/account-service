package com.account.dto.ledger;

import com.account.domain.ledger.LedgerType;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingVoucherEntryResponseDto {

    private Long id;

    private Long ledgerId;

    private String ledgerName;

    private String ledgerCode;

    private LedgerType ledgerType;

    private BigDecimal debitAmount;

    private BigDecimal creditAmount;

    private String narration;

    private Integer displayOrder;
}