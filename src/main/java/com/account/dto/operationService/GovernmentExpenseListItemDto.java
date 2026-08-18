package com.account.dto.operationService;

import com.account.dto.ledger.AccountingVoucherEntryResponseDto;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentExpenseListItemDto {

    private Long operationExpenseId;
    private Long voucherId;
    private String voucherNumber;
    private LocalDate voucherDate;

    private Long projectId;
    private String projectNo;
    private String projectName;

    private Long clientCompanyId;
    private String clientCompanyName;
    private Long clientUnitId;
    private String clientUnitName;

    private String expensePaidBy;
    private Long partyLedgerId;
    private String partyLedgerCode;
    private String partyLedgerName;

    private BigDecimal amount;
    private String status;
    private String narration;
    private List<AccountingVoucherEntryResponseDto> entries;

    private boolean fundTransferPosted;
    private boolean paymentPosted;
    private Long fundTransferVoucherId;
    private Long paymentVoucherId;
    private LocalDateTime postedAt;
}