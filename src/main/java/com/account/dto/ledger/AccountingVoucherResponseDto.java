package com.account.dto.ledger;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingVoucherResponseDto {

    private Long id;

    private String voucherNumber;

    private VoucherType voucherType;

    private LocalDate voucherDate;

    private VoucherSourceType sourceType;

    private Long sourceId;

    private VoucherStatus status;

    private BigDecimal totalDebit;

    private BigDecimal totalCredit;

    private String narration;

    private List<AccountingVoucherEntryResponseDto> entries;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}