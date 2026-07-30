package com.account.dto.vendor;

import com.account.dto.ledger.AccountingVoucherResponseDto;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPaymentApprovalAccountingResult {

    private BigDecimal price;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalGstAmount;
    private BigDecimal grossInvoiceAmount;
    private BigDecimal tdsAmount;
    private BigDecimal vendorNetPayableAmount;

    private Long purchaseLedgerId;
    private Long inputCgstLedgerId;
    private Long inputSgstLedgerId;
    private Long inputIgstLedgerId;
    private Long tdsPayableLedgerId;

    private AccountingVoucherResponseDto voucher;
    private boolean alreadyPosted;
}
