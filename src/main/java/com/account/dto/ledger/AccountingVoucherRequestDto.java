package com.account.dto.ledger;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingVoucherRequestDto {

    @NotNull(message = "Voucher type is required")
    private VoucherType voucherType;

    private LocalDate voucherDate;

    private VoucherSourceType sourceType = VoucherSourceType.MANUAL;

    private Long sourceId;

    private String narration;

    @Valid
    @NotEmpty(message = "At least two voucher entries are required")
    private List<AccountingVoucherEntryRequestDto> entries;
}