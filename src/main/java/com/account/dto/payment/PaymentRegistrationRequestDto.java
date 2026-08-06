package com.account.dto.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
public class PaymentRegistrationRequestDto {

    /*
     * Estimate against which the payment is being registered.
     */
    @NotNull(message = "Estimate ID is required")
    @Positive(message = "Estimate ID must be greater than zero")
    private Long estimateId;

    /*
     * Actual amount received in Bank/Cash/Payment Gateway.
     *
     * Precision:
     * - Maximum 3 decimal places.
     *
     * Zero is allowed only for the initial PURCHASE_ORDER registration.
     * PaymentServiceImpl performs the conditional payment-type validation.
     */
    @NotNull(message = "Payment amount is required")
    @PositiveOrZero(message = "Payment amount must be zero or positive")
    @Digits(
            integer = 16,
            fraction = 3,
            message = "Payment amount can have a maximum of 3 decimal places"
    )
    private BigDecimal amount;

    /*
     * Optional only because an initial zero-value PURCHASE_ORDER registration
     * does not represent an actual payment.
     *
     * PaymentServiceImpl must require this for every positive payment.
     */
    private LocalDate paymentDate;

    @Size(
            max = 50,
            message = "Payment mode cannot exceed 50 characters"
    )
    private String paymentMode;

    @Size(
            max = 150,
            message = "Transaction reference cannot exceed 150 characters"
    )
    private String transactionReference;

    @Size(
            max = 1000,
            message = "Payment proof URL cannot exceed 1000 characters"
    )
    private String paymentProof;

    /*
     * Required for positive Bank/Cash/Payment Gateway receipts.
     * Not required for an initial zero-value PURCHASE_ORDER registration.
     */
    @Positive(message = "Bank ledger ID must be greater than zero")
    private Long bankLedgerId;

    /*
     * Required for PURCHASE_ORDER depending on the business flow.
     */
    @PositiveOrZero(
            message = "Payment terms days cannot be negative"
    )
    private Integer paymentTermsDays;

    @Size(
            max = 250,
            message = "Payment terms cannot exceed 250 characters"
    )
    private String paymentTerms;

    @Size(
            max = 1000,
            message = "Remarks cannot exceed 1000 characters"
    )
    private String remarks;

    @NotNull(message = "Payment type ID is required")
    @Positive(message = "Payment type ID must be greater than zero")
    private Long paymentTypeId;

    /*
     * EPR fields.
     */
    @Size(
            max = 50,
            message = "EPR financial year cannot exceed 50 characters"
    )
    private String eprFinancialYear;

    @Size(
            max = 150,
            message = "EPR portal registration number cannot exceed 150 characters"
    )
    private String eprPortalRegistrationNumber;

    @Size(
            max = 150,
            message = "EPR certificate or invoice number cannot exceed 150 characters"
    )
    private String eprCertificateOrInvoiceNumber;

    /*
     * Government fee.
     */
    @Builder.Default
    private Boolean governmentFeeActive = Boolean.FALSE;

    @Valid
    private GovernmentFeeRequestDto governmentFee;

    /*
     * TDS.
     *
     * Final TDS is calculated and rounded to a whole rupee in the service.
     */
    @Builder.Default
    private Boolean tdsActive = Boolean.FALSE;

    @Valid
    private TdsRequestDto tds;

    /*
     * Purchase Order fields.
     *
     * These are conditionally required when payment type is PURCHASE_ORDER.
     */
    @Size(
            max = 150,
            message = "PO number cannot exceed 150 characters"
    )
    private String poNumber;

    @Size(
            max = 1000,
            message = "PO attachment URL cannot exceed 1000 characters"
    )
    private String poAttachmentUrl;



}