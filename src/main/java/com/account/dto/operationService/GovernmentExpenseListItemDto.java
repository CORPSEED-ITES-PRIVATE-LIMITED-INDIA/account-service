package com.account.dto.operationService;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GovernmentExpenseListItemDto {

    private Long operationExpenseId;

    private Long voucherId;
    private String voucherNumber;
    private LocalDate voucherDate;

    private BigDecimal amount;

    private String status;
    private String narration;

    private boolean fundTransferPosted;
    private boolean paymentPosted;

    private Long fundTransferVoucherId;
    private Long paymentVoucherId;

    private LocalDateTime postedAt;
}