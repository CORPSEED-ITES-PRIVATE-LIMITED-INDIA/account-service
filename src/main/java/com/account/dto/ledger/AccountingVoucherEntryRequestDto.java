package com.account.dto.ledger;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingVoucherEntryRequestDto {

    @NotNull(message = "Ledger ID is required")
    private Long ledgerId;

    private BigDecimal debitAmount = BigDecimal.ZERO;

    private BigDecimal creditAmount = BigDecimal.ZERO;

    private String narration;
}