package com.account.dto.vendor;

import com.account.enm.VendorVoucherLedgerSource;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorVoucherEntryRequestDto {

    @NotNull(message = "Ledger source is required")
    private VendorVoucherLedgerSource ledgerSource;

    /*
     * Required only when ledgerSource = EXISTING_LEDGER.
     *
     * Do not send ledgerId when ledgerSource = VENDOR_LEDGER.
     */
    private Long ledgerId;

    @Builder.Default
    @DecimalMin(
            value = "0.00",
            message = "Debit amount cannot be negative"
    )
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(
            value = "0.00",
            message = "Credit amount cannot be negative"
    )
    private BigDecimal creditAmount = BigDecimal.ZERO;

    private String narration;
}