package com.account.dto.ledger;

import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class LedgerMasterRequestDto {

    @NotBlank(message = "Ledger name is required")
    private String ledgerName;

    @NotNull(message = "Ledger type is required")
    private LedgerType ledgerType;

    @NotNull(message = "Ledger group ID is required")
    private Long ledgerGroupId;

    // Optional for customer/vendor ledgers
    private Long companyId;
    private Long unitId;
    private Long contactId;

    private String gstNo;
    private String panNo;

    // Optional for bank ledger
    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String branchName;

    private BigDecimal openingBalance = BigDecimal.ZERO;
    private DebitCredit openingBalanceType;

    private Boolean active = true;
}