package com.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentFeeFundTransferPostingRequestDto {

    private Long operationExpenseId;

    private Long projectId;

    private String projectNo;

    private Long fromBankLedgerId;

    private String fromBankName;

    private Long toBankLedgerId;

    private String toBankName;

    private BigDecimal amount;

    private LocalDate transferDate;

    private String transferReference;

    private String transferProofUrl;

    private Long transferredByUserId;

    private String transferredByUserName;

    private String narration;
}