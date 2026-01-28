package com.account.dto.payment;

import com.account.domain.UnbilledStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRegistrationResponseDto {

    private Long paymentReceiptId;
    private String unbilledNumber;           // Created or existing
    private UnbilledStatus unbilledStatus;
    private boolean invoiceGenerated;
    private String invoiceNumber;            // If generated
    private BigDecimal invoiceAmount;
    private String message;
}