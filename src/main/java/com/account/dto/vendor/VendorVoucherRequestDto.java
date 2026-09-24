package com.account.dto.vendor;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorVoucherRequestDto {

    /*
     * PURCHASE_INVOICE
     * PAYMENT
     * JOURNAL
     */
    @NotNull(message = "Voucher type is required")
    private VoucherType voucherType;

    /*
     * PROCUREMENT_VENDOR_INVOICE
     * PROCUREMENT_VENDOR_PAYMENT
     * VENDOR_OPENING_BALANCE
     */
    @NotNull(message = "Voucher source type is required")
    private VoucherSourceType sourceType;

    /*
     * Source record ID from Operation Service.
     *
     * Examples:
     * Vendor invoice ID
     * Procurement payment request ID
     * Opening balance request ID
     */
    @NotNull(message = "Voucher source ID is required")
    @Positive(message = "Voucher source ID must be greater than zero")
    private Long sourceId;

    @NotNull(message = "Voucher date is required")
    private LocalDate voucherDate;

    private String narration;

    @Valid
    @NotEmpty(message = "Voucher entries are required")
    @Builder.Default
    private List<VendorVoucherEntryRequestDto> entries =
            new ArrayList<>();
}