package com.account.dto.ledger;

import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
public class LedgerMasterResponseDto {

    private Long id;

    private String ledgerName;
    private String ledgerCode;
    private LedgerType ledgerType;

    private Long ledgerGroupId;
    private String ledgerGroupName;
    private LedgerGroupType ledgerGroupType;

    private Long companyId;
    private String companyName;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country;
    private String pinCode;
    private String fullAddress;

    private Long unitId;
    private String unitName;

    private Long contactId;
    private String contactName;

    private String gstNo;
    private String panNo;

    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String branchName;

    private BigDecimal openingBalance;
    private DebitCredit openingBalanceType;

    private BigDecimal currentBalance;
    private DebitCredit currentBalanceType;

    private Boolean systemCreated;
    private Boolean active;
    private Boolean deleted;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<LedgerTransactionResponseDto> transactions;

}