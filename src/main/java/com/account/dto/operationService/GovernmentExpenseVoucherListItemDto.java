package com.account.dto.operationService;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentExpenseVoucherListItemDto {

    private Long voucherId;

    private String voucherNumber;

    private VoucherType voucherType;

    private LocalDate voucherDate;

    /**
     * This is operationExpenseId for project-expense vouchers.
     */
    private Long operationExpenseId;

    private VoucherSourceType sourceType;

    private VoucherStatus status;

    private BigDecimal amount;

    private BigDecimal totalDebit;

    private BigDecimal totalCredit;

    private String narration;

    private LocalDateTime createdAt;
}