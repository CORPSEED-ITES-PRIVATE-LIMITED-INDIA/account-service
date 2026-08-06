package com.account.dto.operationService;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
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

    @NotNull(message = "Operation expense ID is required")
    @Positive(message = "Operation expense ID must be greater than zero")
    private Long operationExpenseId;

    private Long projectId;
    private String projectNo;

    @NotNull(message = "Source bank ledger ID is required")
    @Positive(message = "Source bank ledger ID must be greater than zero")
    private Long fromBankLedgerId;
    private String fromBankName;

    @NotNull(message = "Destination bank ledger ID is required")
    @Positive(message = "Destination bank ledger ID must be greater than zero")
    private Long toBankLedgerId;
    private String toBankName;

    @NotNull(message = "Transfer amount is required")
    @DecimalMin(value = "0.001", message = "Transfer amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Transfer date is required")
    @PastOrPresent(message = "Transfer date cannot be in the future")
    private LocalDate transferDate;

    private String transferReference;
    private String transferProofUrl;
    private Long transferredByUserId;
    private String transferredByUserName;
    private String narration;
}
