package com.account.dto.operationService;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class GovernmentFeePostingRequestDto {

    @NotNull(message = "Operation expense ID is required")
    @Positive(message = "Operation expense ID must be greater than zero")
    private Long operationExpenseId;

    @Positive(message = "Project ID must be greater than zero")
    private Long projectId;

    @Size(max = 100)
    private String projectNo;

    @Size(max = 255)
    private String projectName;

    @Positive(message = "Client company ID must be greater than zero")
    private Long clientCompanyId;

    @Size(max = 255)
    private String clientCompanyName;

    @Positive(message = "Client unit ID must be greater than zero")
    private Long clientUnitId;

    @Size(max = 255)
    private String clientUnitName;

    @NotBlank(message = "Expense category is required")
    @Size(max = 50)
    private String expenseCategory;

    @NotNull(message = "Approved amount is required")
    @DecimalMin(value = "0.01", message = "Approved amount must be greater than zero")
    private BigDecimal approvedAmount;

    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3)
    private String currencyCode;

    /** Accounts approval/accounting date. */
    @PastOrPresent(message = "Expense posting date cannot be in the future")
    private LocalDate expenseDate;

    @NotNull(message = "Paid by is required")
    private GovernmentFeePaidBy paidBy;

    /** CASH, CASH_DEPOSIT, CHEQUE, DEMAND_DRAFT, NEFT, RTGS, IMPS, UPI, CARD, BANK_TRANSFER or OTHER. */
    @Size(max = 50)
    private String clientPaymentMode;

    /** Account Service LedgerMaster ID of the company bank that received the money. */
    @Positive(message = "Client payment bank ledger ID must be greater than zero")
    private Long clientPaymentBankLedgerId;

    @Size(max = 150)
    private String clientPaymentBankName;

    @PastOrPresent(message = "Client payment date cannot be in the future")
    private LocalDate clientPaymentDate;

    @Size(max = 150)
    private String clientPaymentReference;

    @Size(max = 1000)
    private String clientPaymentProofUrl;

    @Positive(message = "Approved by user ID must be greater than zero")
    private Long approvedByUserId;

    @Size(max = 150)
    private String approvedByUserName;

    @Size(max = 2000)
    private String narration;
}
