package com.account.dto.payment;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private String paymentMode; // UPI, NEFT, CASH, etc.

    @NotBlank(message = "Transaction reference is required")
    private String transactionReference;

    private String remarks;

    @NotNull(message = "Payment type ID is required")
    private Long paymentTypeId;


    /**
     * Financial year the payment relates to (for EPR compliance tracking)
     * Format: "2025-2026"
     * Example: "2025-2026"
     */
    private String eprFinancialYear;  // e.g. "2025-2026" – accounts can see which year's obligation this payment covers

    /**
     * CPCB EPR Portal Registration Number of the client/PIBO
     * This is the unique ID issued after registration on https://eprplastic.cpcb.gov.in
     */
    private String eprPortalRegistrationNumber;

    /**
     * Optional: EPR Certificate Number or EPR Invoice Number (if payment is against certificate purchase/transfer)
     * From portal – helps trace specific compliance transaction
     */
    private String eprCertificateOrInvoiceNumber;

    // You can add more if needed, e.g.:
    // private String eprTargetCategory;   // Cat I, II, III, IV, etc.
    // private BigDecimal eprTargetQuantityInTonnes;
}