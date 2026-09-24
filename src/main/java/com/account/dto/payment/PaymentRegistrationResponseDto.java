package com.account.dto.payment;

import com.account.domain.status.UnbilledStatus;
import lombok.*;

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