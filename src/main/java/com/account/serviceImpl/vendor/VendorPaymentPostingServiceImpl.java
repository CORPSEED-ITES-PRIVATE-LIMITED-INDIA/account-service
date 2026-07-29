package com.account.serviceImpl.vendor;

import com.account.domain.ledger.*;
import com.account.domain.vendor.ExternalVendor;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.vendor.VendorPaymentPostingRequestDto;
import com.account.dto.vendor.VendorPaymentPostingResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.repository.vendor.ExternalVendorRepository;
import com.account.service.ledger.AccountingVoucherService;
import com.account.service.vendor.VendorPaymentPostingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorPaymentPostingServiceImpl
        implements VendorPaymentPostingService {

    private final ExternalVendorRepository externalVendorRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final AccountingVoucherService accountingVoucherService;

    @Override
    @Transactional
    public VendorPaymentPostingResponseDto postVendorPayment(
            VendorPaymentPostingRequestDto request
    ) {
        validateRequest(request);

        ExternalVendor externalVendor =
                externalVendorRepository
                        .findByOperationVendorIdAndDeletedFalse(
                                request.getOperationVendorId()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "External vendor not found for Operation vendor ID: "
                                                + request.getOperationVendorId(),
                                        "EXTERNAL_VENDOR_NOT_FOUND"
                                )
                        );

        if (!externalVendor.isActive()) {
            throw new ValidationException(
                    "Payment cannot be posted because vendor is inactive",
                    "ERR_VENDOR_INACTIVE",
                    "operationVendorId"
            );
        }

        LedgerMaster vendorLedger =
                externalVendor.getLedger();

        if (vendorLedger == null) {
            throw new ValidationException(
                    "Vendor ledger is not linked with external vendor",
                    "ERR_VENDOR_LEDGER_NOT_FOUND",
                    "operationVendorId"
            );
        }

        if (!vendorLedger.isActive()) {
            throw new ValidationException(
                    "Vendor ledger is inactive",
                    "ERR_VENDOR_LEDGER_INACTIVE",
                    "operationVendorId"
            );
        }

        LedgerMaster bankLedger =
                ledgerMasterRepository
                        .findByIdAndDeletedFalse(
                                request.getBankLedgerId()
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Bank ledger not found with ID: "
                                                + request.getBankLedgerId(),
                                        "BANK_LEDGER_NOT_FOUND"
                                )
                        );

        if (!bankLedger.isActive()) {
            throw new ValidationException(
                    "Selected bank ledger is inactive",
                    "ERR_BANK_LEDGER_INACTIVE",
                    "bankLedgerId"
            );
        }

        if (bankLedger.getLedgerType() != LedgerType.BANK
                && bankLedger.getLedgerType() != LedgerType.CASH) {

            throw new ValidationException(
                    "Selected ledger must be a BANK or CASH ledger",
                    "ERR_INVALID_PAYMENT_LEDGER_TYPE",
                    "bankLedgerId"
            );
        }

        BigDecimal grossPayable =
                money(request.getGrossPayableAmount());

        BigDecimal bankPayment =
                money(request.getBankPaymentAmount());

        BigDecimal tdsAmount =
                money(request.getTdsAmount());

        /*
         * Vendor Dr = Bank Cr + TDS Payable Cr
         */
        BigDecimal expectedGross =
                bankPayment
                        .add(tdsAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (grossPayable.compareTo(expectedGross) != 0) {
            throw new ValidationException(
                    "Gross payable amount must equal bank payment amount plus TDS amount. "
                            + "Gross payable: " + grossPayable
                            + ", bank payment: " + bankPayment
                            + ", TDS: " + tdsAmount,
                    "ERR_VENDOR_PAYMENT_TOTAL_MISMATCH",
                    "grossPayableAmount"
            );
        }

        List<AccountingVoucherEntryRequestDto> entries =
                new ArrayList<>();

        // =====================================================
        // DR VENDOR LEDGER
        // Liability toward vendor is reduced.
        // =====================================================

        entries.add(
                AccountingVoucherEntryRequestDto.builder()
                        .ledgerId(vendorLedger.getId())
                        .debitAmount(grossPayable)
                        .creditAmount(zero())
                        .narration(
                                "Vendor payment settled for "
                                        + externalVendor.getVendorName()
                        )
                        .build()
        );

        // =====================================================
        // CR BANK LEDGER
        // Money leaves company bank account.
        // =====================================================

        if (bankPayment.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(
                    AccountingVoucherEntryRequestDto.builder()
                            .ledgerId(bankLedger.getId())
                            .debitAmount(zero())
                            .creditAmount(bankPayment)
                            .narration(
                                    buildBankNarration(request)
                            )
                            .build()
            );
        }

        // =====================================================
        // CR TDS PAYABLE
        // TDS deducted but payable to Government.
        // =====================================================

        if (tdsAmount.compareTo(BigDecimal.ZERO) > 0) {

            if (request.getTdsPayableLedgerId() == null
                    || request.getTdsPayableLedgerId() <= 0) {

                throw new ValidationException(
                        "TDS Payable ledger ID is required when TDS amount is greater than zero",
                        "ERR_TDS_PAYABLE_LEDGER_REQUIRED",
                        "tdsPayableLedgerId"
                );
            }

            LedgerMaster tdsPayableLedger =
                    ledgerMasterRepository
                            .findByIdAndDeletedFalse(
                                    request.getTdsPayableLedgerId()
                            )
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "TDS Payable ledger not found with ID: "
                                                    + request.getTdsPayableLedgerId(),
                                            "TDS_PAYABLE_LEDGER_NOT_FOUND"
                                    )
                            );

            if (!tdsPayableLedger.isActive()) {
                throw new ValidationException(
                        "TDS Payable ledger is inactive",
                        "ERR_TDS_PAYABLE_LEDGER_INACTIVE",
                        "tdsPayableLedgerId"
                );
            }

            if (tdsPayableLedger.getLedgerType()
                    != LedgerType.TDS_PAYABLE) {

                throw new ValidationException(
                        "Selected ledger is not a TDS Payable ledger",
                        "ERR_INVALID_TDS_PAYABLE_LEDGER",
                        "tdsPayableLedgerId"
                );
            }

            entries.add(
                    AccountingVoucherEntryRequestDto.builder()
                            .ledgerId(tdsPayableLedger.getId())
                            .debitAmount(zero())
                            .creditAmount(tdsAmount)
                            .narration(
                                    buildTdsNarration(request)
                            )
                            .build()
            );
        }

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.PAYMENT)
                        .voucherDate(request.getPaymentDate())
                        .sourceType(
                                VoucherSourceType
                                        .PROCUREMENT_VENDOR_PAYMENT
                        )
                        .sourceId(
                                request.getProcurementPaymentRequestId()
                        )
                        .narration(
                                buildVoucherNarration(
                                        externalVendor,
                                        request
                                )
                        )
                        .entries(entries)
                        .build();

        /*
         * This call actually creates:
         *
         * accounting_voucher
         * accounting_voucher_entry
         *
         * and updates ledger balances.
         */
        AccountingVoucherResponseDto voucherResponse =
                accountingVoucherService.createVoucher(
                        voucherRequest
                );

        log.info(
                "Vendor payment voucher posted. operationVendorId={}, "
                        + "procurementPaymentRequestId={}, voucherId={}, "
                        + "voucherNumber={}, grossPayable={}, bankPayment={}, tds={}",
                request.getOperationVendorId(),
                request.getProcurementPaymentRequestId(),
                voucherResponse.getId(),
                voucherResponse.getVoucherNumber(),
                grossPayable,
                bankPayment,
                tdsAmount
        );

        return VendorPaymentPostingResponseDto.builder()
                .operationVendorId(
                        request.getOperationVendorId()
                )
                .procurementPaymentRequestId(
                        request.getProcurementPaymentRequestId()
                )
                .externalVendorId(
                        externalVendor.getId()
                )
                .vendorLedgerId(
                        vendorLedger.getId()
                )
                .vendorLedgerName(
                        vendorLedger.getLedgerName()
                )
                .voucherId(
                        voucherResponse.getId()
                )
                .voucherNumber(
                        voucherResponse.getVoucherNumber()
                )
                .voucherType(
                        voucherResponse.getVoucherType().name()
                )
                .sourceType(
                        voucherResponse.getSourceType().name()
                )
                .sourceId(
                        voucherResponse.getSourceId()
                )
                .voucherDate(
                        voucherResponse.getVoucherDate()
                )
                .totalDebit(
                        voucherResponse.getTotalDebit()
                )
                .totalCredit(
                        voucherResponse.getTotalCredit()
                )
                .grossPayableAmount(grossPayable)
                .bankPaymentAmount(bankPayment)
                .tdsAmount(tdsAmount)
                .status(
                        voucherResponse.getStatus().name()
                )
                .postedAt(LocalDateTime.now())
                .message(
                        "Vendor payment voucher posted successfully"
                )
                .build();
    }

    private void validateRequest(
            VendorPaymentPostingRequestDto request
    ) {
        if (request == null) {
            throw new ValidationException(
                    "Vendor payment request is required",
                    "ERR_VENDOR_PAYMENT_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (request.getOperationVendorId() == null
                || request.getOperationVendorId() <= 0) {

            throw new ValidationException(
                    "Valid Operation vendor ID is required",
                    "ERR_OPERATION_VENDOR_ID_REQUIRED",
                    "operationVendorId"
            );
        }

        if (request.getProcurementPaymentRequestId() == null
                || request.getProcurementPaymentRequestId() <= 0) {

            throw new ValidationException(
                    "Valid procurement payment request ID is required",
                    "ERR_PROCUREMENT_PAYMENT_REQUEST_ID_REQUIRED",
                    "procurementPaymentRequestId"
            );
        }

        if (request.getPaymentDate() == null) {
            throw new ValidationException(
                    "Payment date is required",
                    "ERR_PAYMENT_DATE_REQUIRED",
                    "paymentDate"
            );
        }

        if (money(request.getGrossPayableAmount())
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new ValidationException(
                    "Gross payable amount must be greater than zero",
                    "ERR_GROSS_PAYABLE_AMOUNT_REQUIRED",
                    "grossPayableAmount"
            );
        }

        if (money(request.getBankPaymentAmount())
                .compareTo(BigDecimal.ZERO) < 0) {

            throw new ValidationException(
                    "Bank payment amount cannot be negative",
                    "ERR_INVALID_BANK_PAYMENT_AMOUNT",
                    "bankPaymentAmount"
            );
        }

        if (money(request.getTdsAmount())
                .compareTo(BigDecimal.ZERO) < 0) {

            throw new ValidationException(
                    "TDS amount cannot be negative",
                    "ERR_INVALID_TDS_AMOUNT",
                    "tdsAmount"
            );
        }
    }

    private String buildBankNarration(
            VendorPaymentPostingRequestDto request
    ) {
        String narration = "Vendor payment released";

        if (hasText(request.getTransactionReference())) {
            narration += ", transaction reference: "
                    + request.getTransactionReference().trim();
        }

        return narration;
    }

    private String buildTdsNarration(
            VendorPaymentPostingRequestDto request
    ) {
        String narration = "TDS deducted from vendor payment";

        if (hasText(request.getTdsSection())) {
            narration += " under section "
                    + request.getTdsSection().trim();
        }

        return narration;
    }

    private String buildVoucherNarration(
            ExternalVendor vendor,
            VendorPaymentPostingRequestDto request
    ) {
        if (hasText(request.getNarration())) {
            return request.getNarration().trim();
        }

        String narration =
                "Payment made to vendor "
                        + vendor.getVendorName();

        if (hasText(request.getPurchaseOrderNumber())) {
            narration += ", PO: "
                    + request.getPurchaseOrderNumber().trim();
        }

        if (hasText(request.getVendorInvoiceNumber())) {
            narration += ", vendor invoice: "
                    + request.getVendorInvoiceNumber().trim();
        }

        if (hasText(request.getTransactionReference())) {
            narration += ", transaction reference: "
                    + request.getTransactionReference().trim();
        }

        return narration;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? zero()
                : value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private boolean hasText(String value) {
        return value != null
                && !value.trim().isEmpty();
    }
}