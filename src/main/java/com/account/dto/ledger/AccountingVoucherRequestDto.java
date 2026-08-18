package com.account.dto.ledger;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherType;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingVoucherRequestDto {

    private VoucherType voucherType;
    private LocalDate voucherDate;
    private VoucherSourceType sourceType;
    private Long sourceId;
    private String narration;
    private List<AccountingVoucherEntryRequestDto> entries;

    private Long projectId;
    private String projectNo;
    private String projectName;

    private Long clientCompanyId;
    private String clientCompanyName;
    private Long clientUnitId;
    private String clientUnitName;

    private String expensePaidBy;
    private Long partyLedgerId;
}