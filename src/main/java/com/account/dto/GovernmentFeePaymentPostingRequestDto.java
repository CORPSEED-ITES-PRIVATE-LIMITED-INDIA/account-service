package com.account.dto.operationService;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class GovernmentFeePaymentPostingRequestDto {

    @NotNull(message = "Operation expense ID is required")
    @Positive(message = "Operation expense ID must be greater than zero")
    private Long operationExpenseId;

    private Long projectId;
    private String projectNo;

    @NotNull(message = "Paid by is required")
    private GovernmentFeePaidBy paidBy;

    @NotNull(message = "Payment bank ledger ID is required")
    @Positive(message = "Payment bank ledger ID must be greater than zero")
    private Long paymentBankLedgerId;
    private String paymentBankName;

    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.001", message = "Payment amount must be greater than zero")
    private BigDecimal amount;

    private String currencyCode;

    @NotNull(message = "Payment date is required")
    @PastOrPresent(message = "Payment date cannot be in the future")
    private LocalDate paymentDate;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode;

    @NotBlank(message = "Payment reference is required")
    private String paymentReference;

    @NotBlank(message = "Payment receipt URL is required")
    private String paymentReceiptUrl;

    private Long paidByUserId;
    private String paidByUserName;
    private String narration;
}
