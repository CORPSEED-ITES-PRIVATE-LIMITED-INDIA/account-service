package com.account.dto.payment;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRegistrationRequestDto {

    @NotNull(message = "Estimate ID is required")
    private Long estimateId;

    @NotNull(message = "Payment amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode;

    @NotBlank(message = "Transaction reference is required")
    private String transactionReference;

    private String remarks;

    @NotNull(message = "Payment type ID is required")
    private Long paymentTypeId;

    private String eprFinancialYear;
    private String eprPortalRegistrationNumber;
    private String eprCertificateOrInvoiceNumber;

    private Boolean governmentFeeActive = false;

    private GovernmentFeeRequestDto governmentFee;

    private Boolean tdsActive = false;

    private TdsRequestDto tds;
}