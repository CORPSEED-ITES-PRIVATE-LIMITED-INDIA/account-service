package com.account.dto.vendor;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPaymentPostingResponseDto {

    private Long operationVendorId;

    private Long procurementPaymentRequestId;

    private Long externalVendorId;

    private Long vendorLedgerId;

    private String vendorLedgerName;

    private Long voucherId;

    private String voucherNumber;

    private String voucherType;

    private String sourceType;

    private Long sourceId;

    private LocalDate voucherDate;

    private BigDecimal totalDebit;

    private BigDecimal totalCredit;

    private BigDecimal grossPayableAmount;

    private BigDecimal bankPaymentAmount;

    private BigDecimal tdsAmount;

    private String status;

    private LocalDateTime postedAt;

    private String message;
}