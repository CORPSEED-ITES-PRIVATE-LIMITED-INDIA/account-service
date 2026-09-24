package com.account.dto.vendor;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VendorPaymentPostingRequestDto {

    /*
     * Vendor ID from Operation Service.
     * Used to find ExternalVendor and vendor ledger.
     */
    @NotNull(message = "Operation vendor ID is required")
    @Positive(message = "Operation vendor ID must be greater than zero")
    private Long operationVendorId;

    /*
     * Procurement payment request ID from Operation Service.
     * Used as AccountingVoucher.sourceId.
     */
    @NotNull(message = "Procurement payment request ID is required")
    @Positive(message = "Procurement payment request ID must be greater than zero")
    private Long procurementPaymentRequestId;

    private Long purchaseOrderId;

    private String purchaseOrderNumber;

    private String vendorInvoiceNumber;

    private LocalDate vendorInvoiceDate;

    /*
     * Date on which payment was released.
     */
    @NotNull(message = "Payment date is required")
    private LocalDate paymentDate;

    /*
     * Vendor amount being settled.
     *
     * Example:
     * Invoice amount = 118000
     * TDS = 2000
     * Bank payment = 116000
     *
     * grossPayableAmount = 118000
     */
    @NotNull(message = "Gross payable amount is required")
    @DecimalMin(
            value = "0.01",
            message = "Gross payable amount must be greater than zero"
    )
    private BigDecimal grossPayableAmount;

    /*
     * Actual amount transferred to vendor.
     */
    @NotNull(message = "Bank payment amount is required")
    @DecimalMin(
            value = "0.00",
            message = "Bank payment amount cannot be negative"
    )
    private BigDecimal bankPaymentAmount;

    /*
     * TDS deducted from vendor payment.
     * Send zero when TDS is not deducted.
     */
    @Builder.Default
    @DecimalMin(
            value = "0.00",
            message = "TDS amount cannot be negative"
    )
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    private String tdsSection;

    private BigDecimal tdsPercentage;

    /*
     * Company bank ledger from which payment was made.
     */
    @NotNull(message = "Bank ledger ID is required")
    @Positive(message = "Bank ledger ID must be greater than zero")
    private Long bankLedgerId;

    /*
     * Required only when TDS amount is greater than zero.
     *
     * Alternatively, Account Service can automatically find or create
     * the TDS Payable ledger.
     */
    private Long tdsPayableLedgerId;

    private String transactionReference;

    private String paymentMode;

    private String narration;

    private Long approvedByOperationUserId;
}