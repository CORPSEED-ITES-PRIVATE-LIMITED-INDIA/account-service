package com.account.dto.dashboard;

import com.account.domain.status.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RecentPaymentItemDto {

    private Long paymentReceiptId;

    private Long companyId;
    private String companyName;

    private BigDecimal amount;

    private LocalDate paymentDate;
    private String paymentMode;

    private PaymentStatus paymentStatus;

    private String displayStatus; // Received, Clearing, Rejected

}