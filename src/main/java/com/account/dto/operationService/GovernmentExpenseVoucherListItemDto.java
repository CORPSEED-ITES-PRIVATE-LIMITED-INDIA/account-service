package com.account.dto.operationService;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
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
public class GovernmentExpenseVoucherListItemDto {

    private Long voucherId;
    private String voucherNumber;
    private VoucherType voucherType;
    private LocalDate voucherDate;
    private Long operationExpenseId;
    private VoucherSourceType sourceType;
    private VoucherStatus status;

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
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String narration;
    private List<AccountingVoucherEntryResponseDto> entries;
    private LocalDateTime createdAt;

    // ---- Organization / letterhead details ----
    private String organizationName;
    private String organizationAddressLine1;
    private String organizationAddressLine2;
    private String organizationCity;
    private String organizationState;
    private String organizationCountry;
    private String organizationPinCode;
    private String organizationGstNo;
    private String organizationPanNo;
    private String organizationCinNumber;
    private String organizationEmail;
    private String organizationPhone;
    private String organizationWebsite;
    private String organizationLogoUrl;
}