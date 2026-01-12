// Request DTO - What Salesperson sends
package com.account.dto.payment;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
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
    private LocalDateTime paymentDate;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode;  // UPI, NEFT, CASH, etc.

    @NotBlank(message = "Transaction reference is required")
    private String transactionReference;

    private String remarks;

    @NotNull(message = "Payment type ID is required")
    private Long paymentTypeId;

    // Optional: For INSTALLMENT/Milestone type
    private Long milestoneId;  // If applicable
}