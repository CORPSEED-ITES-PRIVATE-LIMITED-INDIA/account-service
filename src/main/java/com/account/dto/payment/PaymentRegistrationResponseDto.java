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
    private String unbilledNumber;
    private UnbilledStatus unbilledStatus;
    private String message;
}