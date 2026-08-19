package com.account.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
public class PaymentRegistrationRequestDto {

    @NotNull(message = "Estimate ID is required")
    private Long estimateId;

    /**
     * Actual amount credited to Bank/Cash/Payment Gateway.
     * For TDS payments, settlement = amount + backend-calculated TDS.
     */
    @NotNull(message = "Payment amount is required")
    @PositiveOrZero(message = "Amount must be zero or positive")
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    private String paymentMode;
    private String transactionReference;
    private String paymentProof;
    private Long bankLedgerId;
    private Integer paymentTermsDays;
    private String paymentTerms;
    private String remarks;

    @NotNull(message = "Payment type ID is required")
    private Long paymentTypeId;

    private String eprFinancialYear;
    private String eprPortalRegistrationNumber;
    private String eprCertificateOrInvoiceNumber;

    @Builder.Default
    private Boolean governmentFeeActive = false;
    private GovernmentFeeRequestDto governmentFee;

    @Builder.Default
    private Boolean tdsActive = false;
    private TdsRequestDto tds;

    private String poNumber;
    private String poAttachmentUrl;
}
