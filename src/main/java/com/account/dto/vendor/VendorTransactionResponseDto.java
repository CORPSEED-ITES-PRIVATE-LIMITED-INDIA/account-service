package com.account.dto.vendor;

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
public class VendorTransactionResponseDto {

    private Long operationVendorId;
    private Long externalVendorId;

    private String vendorName;

    private Long ledgerId;
    private String ledgerCode;
    private String ledgerName;

    private Long voucherId;
    private String voucherNumber;
    private String voucherType;
    private LocalDate voucherDate;

    private String sourceType;
    private Long sourceId;

    /**
     * For PROCUREMENT_VENDOR_PAYMENT,
     * sourceId is the Operation Service payment request ID.
     */
    private Long procurementPaymentRequestId;

    private BigDecimal debitAmount;
    private BigDecimal creditAmount;

    private String narration;
    private String entryNarration;
    private String voucherStatus;
}