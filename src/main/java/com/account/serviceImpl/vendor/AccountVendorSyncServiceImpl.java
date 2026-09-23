package com.account.serviceImpl.vendor;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.ledger.AccountingVoucher;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.vendor.AccountVendorSyncRequestDto;
import com.account.dto.vendor.AccountVendorSyncResponseDto;
import com.account.dto.vendor.VendorPaymentApprovalRequestDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.ledger.AccountingVoucherRepository;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.ledger.AccountingVoucherService;
import com.account.service.vendor.AccountVendorSyncService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Authoritative Account Service posting for Operation Service vendor payments.
 *
 * Accounting model:
 *
 * PURCHASE_INVOICE
 *   Dr Purchase                 taxable/basic
 *   Dr Input CGST/SGST/IGST    eligible input GST
 *       Cr Vendor              gross invoice
 *
 * PAYMENT
 *   Dr Vendor                  gross invoice
 *       Cr Bank/Cash           actual amount paid
 *       Cr TDS Payable         amount withheld
 *
 * TDS is therefore recognized in the PAYMENT voucher, not in the purchase
 * invoice voucher. Account Service independently recomputes GST/TDS from the
 * incoming base/rates and rejects a mismatched Operation Service snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountVendorSyncServiceImpl implements AccountVendorSyncService {

    private static final String LOG = "[ACCOUNT-VENDOR-SYNC]";

    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

    private static final BigDecimal HUNDRED =
            new BigDecimal("100");

    private static final String PURCHASE_LEDGER_CODE =
            "LED-PROC-PURCHASE";

    private static final String INPUT_CGST_LEDGER_CODE =
            "LED-INPUT-CGST";

    private static final String INPUT_SGST_LEDGER_CODE =
            "LED-INPUT-SGST";

    private static final String INPUT_IGST_LEDGER_CODE =
            "LED-INPUT-IGST";

    private static final String TDS_PAYABLE_LEDGER_CODE =
            "LED-TDS-PAYABLE";

    private final AccountingVoucherService accountingVoucherService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AccountVendorSyncResponseDto syncVendor(
            AccountVendorSyncRequestDto request
    ) {
        validateSyncRequest(request);

        String traceId = "VENDOR-SYNC-" + System.nanoTime();

        VendorPaymentApprovalRequestDto snapshot =
                request.getPaymentApproval();

        GstRegistrationType gstRegistrationType =
                resolveGstRegistrationType(
                        request.getGstRegistrationType(),
                        snapshot != null
                );

        CalculatedAmounts amounts = null;

        if (snapshot != null) {
            amounts = validateAndCalculateSnapshot(
                    snapshot,
                    gstRegistrationType
            );
        }

        LedgerMaster vendorLedger = resolveOrCreateVendorLedger(
                traceId,
                request
        );

        PaymentAccountingResult accountingResult = null;

        if (snapshot != null) {
            accountingResult = postVendorAccounting(
                    traceId,
                    snapshot,
                    vendorLedger,
                    gstRegistrationType,
                    amounts
            );
        }

        AccountVendorSyncResponseDto response = buildResponse(
                request,
                vendorLedger,
                accountingResult
        );

        log.info(
                "{} SYNC-SUCCESS | traceId={} | operationVendorId={} | "
                        + "vendorLedgerId={} | purchaseInvoiceVoucherId={} | "
                        + "paymentVoucherId={} | tds={}",
                LOG,
                traceId,
                request.getOperationVendorId(),
                vendorLedger.getId(),
                response.getVoucherId(),
                response.getPaymentVoucherId(),
                response.getTdsAmount()
        );

        return response;
    }

    // =====================================================================
    // REQUEST / SNAPSHOT VALIDATION
    // =====================================================================

    private void validateSyncRequest(
            AccountVendorSyncRequestDto request
    ) {
        if (request == null) {
            throw new ValidationException(
                    "Vendor synchronization request is required",
                    "ERR_VENDOR_SYNC_REQUEST_REQUIRED",
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

        if (!hasText(request.getVendorName())) {
            throw new ValidationException(
                    "Vendor name is required",
                    "ERR_VENDOR_NAME_REQUIRED",
                    "vendorName"
            );
        }

        if (request.getActive() == null) {
            throw new ValidationException(
                    "Vendor active status is required",
                    "ERR_VENDOR_ACTIVE_STATUS_REQUIRED",
                    "active"
            );
        }

        if (request.getPaymentApproval() != null
                && !Boolean.TRUE.equals(request.getActive())) {
            throw new ValidationException(
                    "Inactive vendor cannot receive a procurement payment posting",
                    "ERR_VENDOR_INACTIVE_FOR_PAYMENT",
                    "active"
            );
        }
    }

    /**
     * Recomputes the accounting snapshot from the base amount and rates.
     * Operation Service still performs its own calculation, but Account Service
     * refuses to post a voucher unless both calculations match exactly at 2 dp.
     */
    private CalculatedAmounts validateAndCalculateSnapshot(
            VendorPaymentApprovalRequestDto request,
            GstRegistrationType registrationType
    ) {
        if (request.getProcurementPaymentRequestId() == null
                || request.getProcurementPaymentRequestId() <= 0) {
            throw new ValidationException(
                    "Valid procurement payment request ID is required",
                    "ERR_PAYMENT_REQUEST_ID_REQUIRED",
                    "paymentApproval.procurementPaymentRequestId"
            );
        }

        if (request.getProcurementOrderId() == null
                || request.getProcurementOrderId() <= 0) {
            throw new ValidationException(
                    "Valid procurement order ID is required",
                    "ERR_PROCUREMENT_ORDER_ID_REQUIRED",
                    "paymentApproval.procurementOrderId"
            );
        }

        BigDecimal price = requiredPositiveMoney(
                request.getPrice(),
                "Price must be greater than zero",
                "ERR_INVALID_VENDOR_PRICE",
                "paymentApproval.price"
        );

        if (request.getTaxableAmount() != null) {
            assertAmountEquals(
                    "taxable amount",
                    price,
                    request.getTaxableAmount(),
                    "paymentApproval.taxableAmount"
            );
        }

        if (hasText(request.getGstRegistrationType())) {
            GstRegistrationType snapshotType =
                    resolveGstRegistrationType(
                            request.getGstRegistrationType(),
                            true
                    );

            if (snapshotType != registrationType) {
                throw new ValidationException(
                        "GST registration type in payment snapshot does not match vendor registration type",
                        "ERR_GST_REGISTRATION_TYPE_MISMATCH",
                        "paymentApproval.gstRegistrationType"
                );
            }
        }

        boolean gstActive = Boolean.TRUE.equals(
                request.getGstActive()
        );

        BigDecimal gstPercentage = rate(
                request.getGstPercentage()
        );

        BigDecimal cgstAmount = ZERO;
        BigDecimal sgstAmount = ZERO;
        BigDecimal igstAmount = ZERO;
        BigDecimal totalGstAmount = ZERO;

        boolean zeroRated =
                registrationType == GstRegistrationType.SEZ
                        || registrationType == GstRegistrationType.INTERNATIONAL;

        if (zeroRated) {
            if (gstActive) {
                throw new ValidationException(
                        registrationType + " transaction must not activate GST posting",
                        "ERR_GST_ACTIVE_FOR_ZERO_RATED",
                        "paymentApproval.gstActive"
                );
            }

            if (gstPercentage.compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(
                        "GST percentage must be zero for " + registrationType,
                        "ERR_ZERO_RATED_GST_PERCENTAGE_NOT_ALLOWED",
                        "paymentApproval.gstPercentage"
                );
            }

        } else if (gstActive) {
            if (registrationType == GstRegistrationType.UNREGISTERED) {
                throw new ValidationException(
                        "Input GST cannot be posted for an UNREGISTERED vendor",
                        "ERR_GST_NOT_ALLOWED_FOR_UNREGISTERED",
                        "paymentApproval.gstActive"
                );
            }

            validatePositiveRate(
                    gstPercentage,
                    "GST percentage",
                    "paymentApproval.gstPercentage"
            );

            totalGstAmount = percentageAmount(
                    price,
                    gstPercentage
            );

            String supplyType = normalizeEnum(
                    request.getGstSupplyType()
            );

            if ("INTRA_STATE".equals(supplyType)) {
                cgstAmount = totalGstAmount.divide(
                        new BigDecimal("2"),
                        MONEY_SCALE,
                        ROUNDING
                );

                sgstAmount = money(
                        totalGstAmount.subtract(cgstAmount)
                );

            } else if ("INTER_STATE".equals(supplyType)) {
                igstAmount = totalGstAmount;

            } else {
                throw new ValidationException(
                        "GST supply type must be INTRA_STATE or INTER_STATE when GST is active",
                        "ERR_INVALID_GST_SUPPLY_TYPE",
                        "paymentApproval.gstSupplyType"
                );
            }

        } else {
            if (gstPercentage.compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(
                        "GST percentage must be zero when GST is inactive",
                        "ERR_GST_PERCENTAGE_NOT_ALLOWED",
                        "paymentApproval.gstPercentage"
                );
            }
        }

        BigDecimal grossInvoiceAmount = money(
                price.add(totalGstAmount)
        );

        boolean tdsActive = Boolean.TRUE.equals(
                request.getTdsActive()
        );

        BigDecimal tdsPercentage = rate(
                request.getTdsPercentage()
        );

        BigDecimal tdsAmount = ZERO;

        if (registrationType == GstRegistrationType.INTERNATIONAL) {
            if (tdsActive
                    || money(request.getTdsAmount()).compareTo(ZERO) > 0) {
                throw new ValidationException(
                        "TDS cannot apply to INTERNATIONAL transactions",
                        "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                        "paymentApproval.tdsActive"
                );
            }

        } else if (tdsActive) {
            validatePositiveRate(
                    tdsPercentage,
                    "TDS percentage",
                    "paymentApproval.tdsPercentage"
            );

            if (request.getTdsBaseAmount() == null) {
                throw new ValidationException(
                        "TDS base amount is required when TDS is active",
                        "ERR_TDS_BASE_AMOUNT_REQUIRED",
                        "paymentApproval.tdsBaseAmount"
                );
            }

            assertAmountEquals(
                    "TDS base amount",
                    price,
                    request.getTdsBaseAmount(),
                    "paymentApproval.tdsBaseAmount"
            );

            tdsAmount = percentageAmount(
                    price,
                    tdsPercentage
            );

            if (request.getTdsAmount() == null) {
                throw new ValidationException(
                        "TDS amount is required when TDS is active",
                        "ERR_TDS_AMOUNT_REQUIRED",
                        "paymentApproval.tdsAmount"
                );
            }

            assertAmountEquals(
                    "TDS amount",
                    tdsAmount,
                    request.getTdsAmount(),
                    "paymentApproval.tdsAmount"
            );

        } else {
            if (tdsPercentage.compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(
                        "TDS percentage must be zero when TDS is inactive",
                        "ERR_TDS_PERCENTAGE_NOT_ALLOWED",
                        "paymentApproval.tdsPercentage"
                );
            }

            if (request.getTdsAmount() != null
                    && money(request.getTdsAmount()).compareTo(ZERO) != 0) {
                throw new ValidationException(
                        "TDS amount must be zero when TDS is inactive",
                        "ERR_TDS_AMOUNT_NOT_ALLOWED",
                        "paymentApproval.tdsAmount"
                );
            }
        }

        BigDecimal vendorNetPayableAmount = money(
                grossInvoiceAmount.subtract(tdsAmount)
        );

        if (vendorNetPayableAmount.compareTo(ZERO) <= 0) {
            throw new ValidationException(
                    "Vendor net payable must be greater than zero",
                    "ERR_VENDOR_NET_PAYABLE_INVALID",
                    "paymentApproval.vendorNetPayableAmount"
            );
        }

        CalculatedAmounts amounts = new CalculatedAmounts(
                price,
                cgstAmount,
                sgstAmount,
                igstAmount,
                totalGstAmount,
                grossInvoiceAmount,
                tdsAmount,
                vendorNetPayableAmount
        );

        validateSuppliedCalculationSnapshot(
                request,
                amounts
        );

        if (hasPaymentReleaseData(request)) {
            validatePaymentReleaseData(
                    request,
                    amounts
            );
        }

        log.info(
                "{} SNAPSHOT-VALIDATED | paymentRequestId={} | price={} | "
                        + "cgst={} | sgst={} | igst={} | totalGst={} | "
                        + "gross={} | tdsActive={} | tdsPercentage={} | "
                        + "tds={} | netPayable={}",
                LOG,
                request.getProcurementPaymentRequestId(),
                amounts.price(),
                amounts.cgstAmount(),
                amounts.sgstAmount(),
                amounts.igstAmount(),
                amounts.totalGstAmount(),
                amounts.grossInvoiceAmount(),
                request.getTdsActive(),
                request.getTdsPercentage(),
                amounts.tdsAmount(),
                amounts.vendorNetPayableAmount()
        );

        return amounts;
    }

    private void validateSuppliedCalculationSnapshot(
            VendorPaymentApprovalRequestDto request,
            CalculatedAmounts amounts
    ) {
        validateOptionalSnapshotAmount(
                "taxable amount",
                request.getTaxableAmount(),
                amounts.price(),
                "paymentApproval.taxableAmount"
        );

        validateOptionalSnapshotAmount(
                "CGST amount",
                request.getCgstAmount(),
                amounts.cgstAmount(),
                "paymentApproval.cgstAmount"
        );

        validateOptionalSnapshotAmount(
                "SGST amount",
                request.getSgstAmount(),
                amounts.sgstAmount(),
                "paymentApproval.sgstAmount"
        );

        validateOptionalSnapshotAmount(
                "IGST amount",
                request.getIgstAmount(),
                amounts.igstAmount(),
                "paymentApproval.igstAmount"
        );

        validateOptionalSnapshotAmount(
                "total GST amount",
                request.getTotalGstAmount(),
                amounts.totalGstAmount(),
                "paymentApproval.totalGstAmount"
        );

        validateOptionalSnapshotAmount(
                "gross invoice amount",
                request.getInvoiceGrossAmount(),
                amounts.grossInvoiceAmount(),
                "paymentApproval.invoiceGrossAmount"
        );

        validateOptionalSnapshotAmount(
                "TDS amount",
                request.getTdsAmount(),
                amounts.tdsAmount(),
                "paymentApproval.tdsAmount"
        );

        validateOptionalSnapshotAmount(
                "vendor net payable amount",
                request.getVendorNetPayableAmount(),
                amounts.vendorNetPayableAmount(),
                "paymentApproval.vendorNetPayableAmount"
        );
    }

    private boolean hasPaymentReleaseData(
            VendorPaymentApprovalRequestDto request
    ) {
        return request.getBankPaymentAmount() != null
                || request.getBankLedgerId() != null
                || request.getPaymentDate() != null
                || request.getPaymentReleasedByOperationUserId() != null
                || hasText(request.getTransactionReference())
                || hasText(request.getPaymentMode());
    }

    private void validatePaymentReleaseData(
            VendorPaymentApprovalRequestDto request,
            CalculatedAmounts amounts
    ) {
        BigDecimal bankPaymentAmount = requiredPositiveMoney(
                request.getBankPaymentAmount(),
                "Bank payment amount must be greater than zero",
                "ERR_INVALID_BANK_PAYMENT_AMOUNT",
                "paymentApproval.bankPaymentAmount"
        );

        if (request.getBankLedgerId() == null
                || request.getBankLedgerId() <= 0) {
            throw new ValidationException(
                    "Bank/Cash ledger ID is required for payment release",
                    "ERR_BANK_LEDGER_REQUIRED",
                    "paymentApproval.bankLedgerId"
            );
        }

        if (request.getPaymentReleasedByOperationUserId() == null
                || request.getPaymentReleasedByOperationUserId() <= 0) {
            throw new ValidationException(
                    "Payment released by Operation user ID is required",
                    "ERR_PAYMENT_RELEASE_USER_REQUIRED",
                    "paymentApproval.paymentReleasedByOperationUserId"
            );
        }

        if (request.getPaymentDate() == null) {
            throw new ValidationException(
                    "Payment date is required",
                    "ERR_PAYMENT_DATE_REQUIRED",
                    "paymentApproval.paymentDate"
            );
        }

        if (!hasText(request.getPaymentMode())) {
            throw new ValidationException(
                    "Payment mode is required",
                    "ERR_PAYMENT_MODE_REQUIRED",
                    "paymentApproval.paymentMode"
            );
        }

        assertAmountEquals(
                "bank payment amount",
                amounts.vendorNetPayableAmount(),
                bankPaymentAmount,
                "paymentApproval.bankPaymentAmount"
        );

        BigDecimal settlement = money(
                bankPaymentAmount.add(amounts.tdsAmount())
        );

        assertAmountEquals(
                "settlement amount",
                amounts.grossInvoiceAmount(),
                settlement,
                "paymentApproval.settlementAmount"
        );

        if (request.getSettlementAmount() == null) {
            throw new ValidationException(
                    "Settlement amount is required for payment release",
                    "ERR_SETTLEMENT_AMOUNT_REQUIRED",
                    "paymentApproval.settlementAmount"
            );
        }

        assertAmountEquals(
                "supplied settlement amount",
                amounts.grossInvoiceAmount(),
                request.getSettlementAmount(),
                "paymentApproval.settlementAmount"
        );
    }

    // =====================================================================
    // ACCOUNTING POSTING
    // =====================================================================

    private PaymentAccountingResult postVendorAccounting(
            String traceId,
            VendorPaymentApprovalRequestDto request,
            LedgerMaster vendorLedger,
            GstRegistrationType registrationType,
            CalculatedAmounts amounts
    ) {
        LedgerMaster purchaseLedger = getOrCreateSystemLedger(
                LedgerType.PURCHASE,
                LedgerGroupType.PURCHASE_ACCOUNTS,
                "Purchases - Procurement",
                PURCHASE_LEDGER_CODE,
                DebitCredit.DEBIT
        );

        LedgerMaster inputCgstLedger = amounts.cgstAmount().compareTo(ZERO) > 0
                ? getOrCreateSystemLedger(
                LedgerType.INPUT_CGST,
                LedgerGroupType.DUTIES_AND_TAXES,
                "Input CGST",
                INPUT_CGST_LEDGER_CODE,
                DebitCredit.DEBIT
        )
                : null;

        LedgerMaster inputSgstLedger = amounts.sgstAmount().compareTo(ZERO) > 0
                ? getOrCreateSystemLedger(
                LedgerType.INPUT_SGST,
                LedgerGroupType.DUTIES_AND_TAXES,
                "Input SGST",
                INPUT_SGST_LEDGER_CODE,
                DebitCredit.DEBIT
        )
                : null;

        LedgerMaster inputIgstLedger = amounts.igstAmount().compareTo(ZERO) > 0
                ? getOrCreateSystemLedger(
                LedgerType.INPUT_IGST,
                LedgerGroupType.DUTIES_AND_TAXES,
                "Input IGST",
                INPUT_IGST_LEDGER_CODE,
                DebitCredit.DEBIT
        )
                : null;

        AccountingVoucherResponseDto invoiceVoucher =
                findPostedVoucherResponse(
                        VoucherSourceType.PROCUREMENT_VENDOR_INVOICE,
                        request.getProcurementPaymentRequestId()
                );

        if (invoiceVoucher != null) {
            validateExistingInvoiceVoucher(
                    invoiceVoucher,
                    vendorLedger,
                    amounts
            );
        } else {
            List<AccountingVoucherEntryRequestDto> entries =
                    new ArrayList<>();

            entries.add(
                    debit(
                            purchaseLedger.getId(),
                            amounts.price(),
                            "Procurement purchase booked"
                    )
            );

            if (inputCgstLedger != null) {
                entries.add(
                        debit(
                                inputCgstLedger.getId(),
                                amounts.cgstAmount(),
                                "Input CGST on procurement purchase"
                        )
                );
            }

            if (inputSgstLedger != null) {
                entries.add(
                        debit(
                                inputSgstLedger.getId(),
                                amounts.sgstAmount(),
                                "Input SGST on procurement purchase"
                        )
                );
            }

            if (inputIgstLedger != null) {
                entries.add(
                        debit(
                                inputIgstLedger.getId(),
                                amounts.igstAmount(),
                                "Input IGST on procurement purchase"
                        )
                );
            }

            entries.add(
                    credit(
                            vendorLedger.getId(),
                            amounts.grossInvoiceAmount(),
                            "Vendor gross liability booked"
                    )
            );

            assertBalancedEntries(
                    entries,
                    "PURCHASE_INVOICE"
            );

            LocalDate invoiceDate =
                    request.getInvoiceDate() != null
                            ? request.getInvoiceDate()
                            : LocalDate.now();

            AccountingVoucherRequestDto voucherRequest =
                    AccountingVoucherRequestDto.builder()
                            .voucherType(VoucherType.PURCHASE_INVOICE)
                            .voucherDate(invoiceDate)
                            .sourceType(
                                    VoucherSourceType.PROCUREMENT_VENDOR_INVOICE
                            )
                            .sourceId(
                                    request.getProcurementPaymentRequestId()
                            )
                            .narration(
                                    buildInvoiceNarration(
                                            request,
                                            amounts
                                    )
                            )
                            .entries(entries)
                            .build();

            invoiceVoucher =
                    accountingVoucherService.createVoucher(
                            voucherRequest
                    );

            validateExistingInvoiceVoucher(
                    invoiceVoucher,
                    vendorLedger,
                    amounts
            );

            log.info(
                    "{} PURCHASE-INVOICE-POSTED | traceId={} | paymentRequestId={} | "
                            + "voucherId={} | gross={} | tds={}",
                    LOG,
                    traceId,
                    request.getProcurementPaymentRequestId(),
                    invoiceVoucher.getId(),
                    amounts.grossInvoiceAmount(),
                    amounts.tdsAmount()
            );
        }

        AccountingVoucherResponseDto paymentVoucher = null;
        LedgerMaster paymentBankLedger = null;
        LedgerMaster tdsPayableLedger = null;

        if (hasPaymentReleaseData(request)) {
            paymentBankLedger = getAndValidatePaymentLedger(
                    request.getBankLedgerId()
            );

            if (amounts.tdsAmount().compareTo(ZERO) > 0) {
                tdsPayableLedger = resolveTdsPayableLedger(
                        request.getTdsPayableLedgerId()
                );
            }

            paymentVoucher = findPostedVoucherResponse(
                    VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT,
                    request.getProcurementPaymentRequestId()
            );

            if (paymentVoucher != null) {
                validateExistingPaymentVoucher(
                        paymentVoucher,
                        vendorLedger,
                        paymentBankLedger,
                        amounts
                );
            } else {
                List<AccountingVoucherEntryRequestDto> entries =
                        new ArrayList<>();

                // Dr vendor for the full gross liability being settled.
                entries.add(
                        debit(
                                vendorLedger.getId(),
                                amounts.grossInvoiceAmount(),
                                "Vendor liability settled"
                        )
                );

                // Cr bank/cash only for actual cash outflow.
                entries.add(
                        credit(
                                paymentBankLedger.getId(),
                                amounts.vendorNetPayableAmount(),
                                "Vendor payment through "
                                        + paymentBankLedger.getLedgerName()
                        )
                );

                // Cr TDS payable for the withheld amount.
                if (tdsPayableLedger != null) {
                    entries.add(
                            credit(
                                    tdsPayableLedger.getId(),
                                    amounts.tdsAmount(),
                                    "TDS withheld from vendor payment"
                            )
                    );
                }

                assertBalancedEntries(
                        entries,
                        "PAYMENT"
                );

                LocalDate paymentDate =
                        request.getPaymentDate() != null
                                ? request.getPaymentDate()
                                : LocalDate.now();

                AccountingVoucherRequestDto voucherRequest =
                        AccountingVoucherRequestDto.builder()
                                .voucherType(VoucherType.PAYMENT)
                                .voucherDate(paymentDate)
                                .sourceType(
                                        VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT
                                )
                                .sourceId(
                                        request.getProcurementPaymentRequestId()
                                )
                                .narration(
                                        buildPaymentNarration(
                                                request,
                                                amounts,
                                                paymentBankLedger
                                        )
                                )
                                .entries(entries)
                                .build();

                paymentVoucher =
                        accountingVoucherService.createVoucher(
                                voucherRequest
                        );

                validateExistingPaymentVoucher(
                        paymentVoucher,
                        vendorLedger,
                        paymentBankLedger,
                        amounts
                );

                log.info(
                        "{} PAYMENT-POSTED | traceId={} | paymentRequestId={} | "
                                + "voucherId={} | vendorDebit={} | bankCredit={} | "
                                + "tdsCredit={}",
                        LOG,
                        traceId,
                        request.getProcurementPaymentRequestId(),
                        paymentVoucher.getId(),
                        amounts.grossInvoiceAmount(),
                        amounts.vendorNetPayableAmount(),
                        amounts.tdsAmount()
                );
            }
        }

        return PaymentAccountingResult.builder()
                .amounts(amounts)
                .invoiceVoucher(invoiceVoucher)
                .paymentVoucher(paymentVoucher)
                .purchaseLedgerId(purchaseLedger.getId())
                .inputCgstLedgerId(id(inputCgstLedger))
                .inputSgstLedgerId(id(inputSgstLedger))
                .inputIgstLedgerId(id(inputIgstLedger))
                .tdsPayableLedgerId(id(tdsPayableLedger))
                .paymentBankLedgerId(id(paymentBankLedger))
                .build();
    }

    // =====================================================================
    // EXISTING VOUCHER VALIDATION
    // =====================================================================

    private void validateExistingInvoiceVoucher(
            AccountingVoucherResponseDto voucher,
            LedgerMaster vendorLedger,
            CalculatedAmounts amounts
    ) {
        validateVoucherHeader(
                voucher,
                VoucherType.PURCHASE_INVOICE,
                VoucherSourceType.PROCUREMENT_VENDOR_INVOICE
        );

        assertAmountEquals(
                "purchase debit",
                amounts.price(),
                sumVoucherDebitByLedgerType(
                        voucher,
                        LedgerType.PURCHASE
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "input CGST debit",
                amounts.cgstAmount(),
                sumVoucherDebitByLedgerType(
                        voucher,
                        LedgerType.INPUT_CGST
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "input SGST debit",
                amounts.sgstAmount(),
                sumVoucherDebitByLedgerType(
                        voucher,
                        LedgerType.INPUT_SGST
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "input IGST debit",
                amounts.igstAmount(),
                sumVoucherDebitByLedgerType(
                        voucher,
                        LedgerType.INPUT_IGST
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "vendor gross credit",
                amounts.grossInvoiceAmount(),
                sumVoucherCreditByLedgerId(
                        voucher,
                        vendorLedger.getId()
                ),
                "voucher.entries"
        );

        // TDS must not be posted in this accounting model's purchase invoice.
        assertAmountEquals(
                "purchase invoice TDS credit",
                ZERO,
                sumVoucherCreditByLedgerType(
                        voucher,
                        LedgerType.TDS_PAYABLE
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "purchase invoice total debit",
                amounts.grossInvoiceAmount(),
                voucher.getTotalDebit(),
                "voucher.totalDebit"
        );

        assertAmountEquals(
                "purchase invoice total credit",
                amounts.grossInvoiceAmount(),
                voucher.getTotalCredit(),
                "voucher.totalCredit"
        );
    }

    private void validateExistingPaymentVoucher(
            AccountingVoucherResponseDto voucher,
            LedgerMaster vendorLedger,
            LedgerMaster bankLedger,
            CalculatedAmounts amounts
    ) {
        validateVoucherHeader(
                voucher,
                VoucherType.PAYMENT,
                VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT
        );

        assertAmountEquals(
                "vendor payment debit",
                amounts.grossInvoiceAmount(),
                sumVoucherDebitByLedgerId(
                        voucher,
                        vendorLedger.getId()
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "bank payment credit",
                amounts.vendorNetPayableAmount(),
                sumVoucherCreditByLedgerId(
                        voucher,
                        bankLedger.getId()
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "TDS payable credit",
                amounts.tdsAmount(),
                sumVoucherCreditByLedgerType(
                        voucher,
                        LedgerType.TDS_PAYABLE
                ),
                "voucher.entries"
        );

        assertAmountEquals(
                "payment voucher total debit",
                amounts.grossInvoiceAmount(),
                voucher.getTotalDebit(),
                "voucher.totalDebit"
        );

        assertAmountEquals(
                "payment voucher total credit",
                amounts.grossInvoiceAmount(),
                voucher.getTotalCredit(),
                "voucher.totalCredit"
        );
    }

    private void validateVoucherHeader(
            AccountingVoucherResponseDto voucher,
            VoucherType expectedType,
            VoucherSourceType expectedSourceType
    ) {
        if (voucher == null) {
            throw new ValidationException(
                    "Accounting voucher response is required",
                    "ERR_ACCOUNTING_VOUCHER_REQUIRED",
                    "voucher"
            );
        }

        if (voucher.getVoucherType() != expectedType) {
            throw new ValidationException(
                    "Unexpected voucher type. Expected "
                            + expectedType
                            + " but found "
                            + voucher.getVoucherType(),
                    "ERR_VOUCHER_TYPE_MISMATCH",
                    "voucher.voucherType"
            );
        }

        if (voucher.getSourceType() != expectedSourceType) {
            throw new ValidationException(
                    "Unexpected voucher source type. Expected "
                            + expectedSourceType
                            + " but found "
                            + voucher.getSourceType(),
                    "ERR_VOUCHER_SOURCE_TYPE_MISMATCH",
                    "voucher.sourceType"
            );
        }

        if (voucher.getStatus() != VoucherStatus.POSTED) {
            throw new ValidationException(
                    "Voucher is not POSTED",
                    "ERR_VOUCHER_NOT_POSTED",
                    "voucher.status"
            );
        }
    }

    // =====================================================================
    // LEDGER RESOLUTION
    // =====================================================================

    private LedgerMaster resolveOrCreateVendorLedger(
            String traceId,
            AccountVendorSyncRequestDto request
    ) {
        String vendorCode = generateVendorLedgerCode(
                request.getOperationVendorId()
        );

        LedgerGroup vendorGroup = getOrCreateLedgerGroup(
                LedgerGroupType.SUNDRY_CREDITORS
        );

        Optional<LedgerMaster> existingOptional =
                ledgerMasterRepository
                        .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                                vendorCode
                        );

        if (existingOptional.isPresent()) {
            LedgerMaster ledger = existingOptional.get();

            if (ledger.getLedgerType() != LedgerType.VENDOR) {
                throw new ValidationException(
                        "Vendor ledger code " + vendorCode
                                + " is mapped to "
                                + ledger.getLedgerType(),
                        "ERR_VENDOR_LEDGER_TYPE_MISMATCH",
                        "ledgerType"
                );
            }

            ledger.setLedgerName(
                    request.getVendorName().trim()
            );
            ledger.setLedgerGroup(vendorGroup);
            ledger.setGstNo(clean(request.getGstNumber()));
            ledger.setPanNo(clean(request.getPan()));
            ledger.setActive(
                    Boolean.TRUE.equals(request.getActive())
            );
            ledger.setDeleted(false);

            LedgerMaster saved =
                    ledgerMasterRepository.saveAndFlush(ledger);

            log.info(
                    "{} VENDOR-LEDGER-RESOLVED | traceId={} | operationVendorId={} | ledgerId={}",
                    LOG,
                    traceId,
                    request.getOperationVendorId(),
                    saved.getId()
            );

            return saved;
        }

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerCode(vendorCode);
        ledger.setLedgerName(request.getVendorName().trim());
        ledger.setLedgerType(LedgerType.VENDOR);
        ledger.setLedgerGroup(vendorGroup);
        ledger.setGstNo(clean(request.getGstNumber()));
        ledger.setPanNo(clean(request.getPan()));
        ledger.setOpeningBalance(ZERO);
        ledger.setOpeningBalanceType(DebitCredit.CREDIT);
        ledger.setCurrentBalance(ZERO);
        ledger.setCurrentBalanceType(DebitCredit.CREDIT);
        ledger.setSystemCreated(true);
        ledger.setActive(Boolean.TRUE.equals(request.getActive()));
        ledger.setDeleted(false);

        try {
            return ledgerMasterRepository.saveAndFlush(ledger);
        } catch (DataIntegrityViolationException exception) {
            return ledgerMasterRepository
                    .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                            vendorCode
                    )
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerMaster getOrCreateSystemLedger(
            LedgerType ledgerType,
            LedgerGroupType groupType,
            String ledgerName,
            String ledgerCode,
            DebitCredit normalBalance
    ) {
        Optional<LedgerMaster> byCode =
                ledgerMasterRepository
                        .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                                ledgerCode
                        );

        if (byCode.isPresent()) {
            return validateSystemLedger(
                    byCode.get(),
                    ledgerType,
                    groupType
            );
        }

        Optional<LedgerMaster> byType =
                ledgerMasterRepository
                        .findByLedgerTypeAndDeletedFalse(
                                ledgerType
                        );

        if (byType.isPresent()) {
            return validateSystemLedger(
                    byType.get(),
                    ledgerType,
                    groupType
            );
        }

        LedgerGroup group = getOrCreateLedgerGroup(
                groupType
        );

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerCode(ledgerCode);
        ledger.setLedgerName(ledgerName);
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(group);
        ledger.setOpeningBalance(ZERO);
        ledger.setOpeningBalanceType(normalBalance);
        ledger.setCurrentBalance(ZERO);
        ledger.setCurrentBalanceType(normalBalance);
        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        try {
            return ledgerMasterRepository.saveAndFlush(ledger);
        } catch (DataIntegrityViolationException exception) {
            return ledgerMasterRepository
                    .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                            ledgerCode
                    )
                    .or(() ->
                            ledgerMasterRepository
                                    .findByLedgerTypeAndDeletedFalse(
                                            ledgerType
                                    )
                    )
                    .map(value ->
                            validateSystemLedger(
                                    value,
                                    ledgerType,
                                    groupType
                            )
                    )
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerMaster validateSystemLedger(
            LedgerMaster ledger,
            LedgerType expectedType,
            LedgerGroupType expectedGroupType
    ) {
        if (ledger.getLedgerType() != expectedType) {
            throw new ValidationException(
                    "System ledger has invalid type. Expected "
                            + expectedType
                            + " but found "
                            + ledger.getLedgerType(),
                    "ERR_SYSTEM_LEDGER_TYPE_MISMATCH",
                    "ledgerType"
            );
        }

        if (ledger.getLedgerGroup() == null
                || ledger.getLedgerGroup().getGroupType()
                != expectedGroupType) {
            throw new ValidationException(
                    "System ledger "
                            + ledger.getLedgerName()
                            + " must belong to "
                            + expectedGroupType,
                    "ERR_SYSTEM_LEDGER_GROUP_MISMATCH",
                    "ledgerGroup"
            );
        }

        if (!ledger.isActive()) {
            ledger.setActive(true);
            ledger = ledgerMasterRepository.save(ledger);
        }

        return ledger;
    }

    private LedgerGroup getOrCreateLedgerGroup(
            LedgerGroupType groupType
    ) {
        Optional<LedgerGroup> existing =
                ledgerGroupRepository
                        .findByGroupTypeAndDeletedFalse(
                                groupType
                        );

        if (existing.isPresent()) {
            LedgerGroup group = existing.get();

            if (!group.isActive() || !group.isSystemDefault()) {
                group.setActive(true);
                group.setSystemDefault(true);
                group = ledgerGroupRepository.saveAndFlush(group);
            }

            return group;
        }

        Optional<LedgerGroup> existingAny =
                ledgerGroupRepository.findByGroupType(groupType);

        if (existingAny.isPresent()) {
            LedgerGroup group = existingAny.get();
            group.setDeleted(false);
            group.setActive(true);
            group.setSystemDefault(true);
            return ledgerGroupRepository.saveAndFlush(group);
        }

        LedgerGroup group = LedgerGroup.builder()
                .name(formatGroupName(groupType))
                .groupType(groupType)
                .description(
                        "System default group for "
                                + formatGroupName(groupType)
                )
                .systemDefault(true)
                .active(true)
                .deleted(false)
                .build();

        try {
            return ledgerGroupRepository.saveAndFlush(group);
        } catch (DataIntegrityViolationException exception) {
            return ledgerGroupRepository
                    .findByGroupType(groupType)
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerMaster getAndValidatePaymentLedger(
            Long bankLedgerId
    ) {
        LedgerMaster bankLedger =
                ledgerMasterRepository
                        .findByIdAndDeletedFalse(
                                bankLedgerId
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Bank/Cash ledger not found with ID: "
                                                + bankLedgerId,
                                        "BANK_LEDGER_NOT_FOUND"
                                )
                        );

        if (!bankLedger.isActive()) {
            throw new ValidationException(
                    "Selected Bank/Cash ledger is inactive",
                    "ERR_BANK_LEDGER_INACTIVE",
                    "paymentApproval.bankLedgerId"
            );
        }

        LedgerType ledgerType = bankLedger.getLedgerType();

        if (ledgerType != LedgerType.BANK
                && ledgerType != LedgerType.CASH
                && ledgerType != LedgerType.PAYMENT_GATEWAY) {
            throw new ValidationException(
                    "Selected payment ledger must be BANK, CASH or PAYMENT_GATEWAY",
                    "ERR_INVALID_PAYMENT_LEDGER",
                    "paymentApproval.bankLedgerId"
            );
        }

        return bankLedger;
    }

    private LedgerMaster resolveTdsPayableLedger(
            Long requestedLedgerId
    ) {
        if (requestedLedgerId != null) {
            LedgerMaster ledger =
                    ledgerMasterRepository
                            .findByIdAndDeletedFalse(
                                    requestedLedgerId
                            )
                            .orElseThrow(() ->
                                    new ResourceNotFoundException(
                                            "TDS Payable ledger not found with ID: "
                                                    + requestedLedgerId,
                                            "TDS_PAYABLE_LEDGER_NOT_FOUND"
                                    )
                            );

            if (!ledger.isActive()) {
                throw new ValidationException(
                        "TDS Payable ledger is inactive",
                        "ERR_TDS_PAYABLE_LEDGER_INACTIVE",
                        "paymentApproval.tdsPayableLedgerId"
                );
            }

            if (ledger.getLedgerType() != LedgerType.TDS_PAYABLE) {
                throw new ValidationException(
                        "Selected TDS ledger must have ledger type TDS_PAYABLE",
                        "ERR_INVALID_TDS_PAYABLE_LEDGER_TYPE",
                        "paymentApproval.tdsPayableLedgerId"
                );
            }

            if (ledger.getLedgerGroup() == null
                    || ledger.getLedgerGroup().getGroupType()
                    != LedgerGroupType.DUTIES_AND_TAXES) {
                throw new ValidationException(
                        "TDS Payable ledger must belong to DUTIES_AND_TAXES",
                        "ERR_INVALID_TDS_PAYABLE_LEDGER_GROUP",
                        "paymentApproval.tdsPayableLedgerId"
                );
            }

            return ledger;
        }

        return getOrCreateSystemLedger(
                LedgerType.TDS_PAYABLE,
                LedgerGroupType.DUTIES_AND_TAXES,
                "TDS Payable",
                TDS_PAYABLE_LEDGER_CODE,
                DebitCredit.CREDIT
        );
    }

    // =====================================================================
    // RESPONSE
    // =====================================================================

    private AccountVendorSyncResponseDto buildResponse(
            AccountVendorSyncRequestDto request,
            LedgerMaster ledger,
            PaymentAccountingResult paymentResult
    ) {
        LedgerGroup ledgerGroup = ledger.getLedgerGroup();

        AccountingVoucherResponseDto invoiceVoucher =
                paymentResult != null
                        ? paymentResult.invoiceVoucher()
                        : null;

        AccountingVoucherResponseDto paymentVoucher =
                paymentResult != null
                        ? paymentResult.paymentVoucher()
                        : null;

        CalculatedAmounts amounts =
                paymentResult != null
                        ? paymentResult.amounts()
                        : null;

        boolean invoicePresent = invoiceVoucher != null;
        boolean paymentPresent = paymentVoucher != null;

        return AccountVendorSyncResponseDto.builder()
                .operationVendorId(request.getOperationVendorId())
                .vendorAccountsSubmissionId(
                        request.getVendorAccountsSubmissionId()
                )
                .vendorFinalizationId(
                        request.getVendorFinalizationId()
                )
                .vendorName(request.getVendorName())

                .ledgerId(ledger.getId())
                .ledgerCode(ledger.getLedgerCode())
                .ledgerName(ledger.getLedgerName())
                .ledgerType(
                        ledger.getLedgerType() != null
                                ? ledger.getLedgerType().name()
                                : null
                )
                .ledgerGroupId(
                        ledgerGroup != null
                                ? ledgerGroup.getId()
                                : null
                )
                .ledgerGroupName(
                        ledgerGroup != null
                                ? ledgerGroup.getName()
                                : null
                )
                .ledgerGroupType(
                        ledgerGroup != null
                                && ledgerGroup.getGroupType() != null
                                ? ledgerGroup.getGroupType().name()
                                : null
                )

                .action("SYNC")
                .active(ledger.isActive())

                // Values returned for Operation Service cross-check.
                .price(amounts != null ? amounts.price() : null)
                .cgstAmount(amounts != null ? amounts.cgstAmount() : null)
                .sgstAmount(amounts != null ? amounts.sgstAmount() : null)
                .igstAmount(amounts != null ? amounts.igstAmount() : null)
                .totalGstAmount(
                        amounts != null
                                ? amounts.totalGstAmount()
                                : null
                )
                .grossInvoiceAmount(
                        amounts != null
                                ? amounts.grossInvoiceAmount()
                                : null
                )
                .tdsAmount(
                        amounts != null
                                ? amounts.tdsAmount()
                                : null
                )
                .vendorNetPayableAmount(
                        amounts != null
                                ? amounts.vendorNetPayableAmount()
                                : null
                )

                .purchaseLedgerId(
                        paymentResult != null
                                ? paymentResult.purchaseLedgerId()
                                : null
                )
                .inputCgstLedgerId(
                        paymentResult != null
                                ? paymentResult.inputCgstLedgerId()
                                : null
                )
                .inputSgstLedgerId(
                        paymentResult != null
                                ? paymentResult.inputSgstLedgerId()
                                : null
                )
                .inputIgstLedgerId(
                        paymentResult != null
                                ? paymentResult.inputIgstLedgerId()
                                : null
                )
                .tdsPayableLedgerId(
                        paymentResult != null
                                ? paymentResult.tdsPayableLedgerId()
                                : null
                )
                .paymentBankLedgerId(
                        paymentResult != null
                                ? paymentResult.paymentBankLedgerId()
                                : null
                )

                .voucherCreated(invoicePresent)
                .voucherId(
                        invoicePresent
                                ? invoiceVoucher.getId()
                                : null
                )
                .voucherNumber(
                        invoicePresent
                                ? invoiceVoucher.getVoucherNumber()
                                : null
                )
                .voucherType(
                        invoicePresent
                                && invoiceVoucher.getVoucherType() != null
                                ? invoiceVoucher.getVoucherType().name()
                                : null
                )
                .voucherSourceType(
                        invoicePresent
                                && invoiceVoucher.getSourceType() != null
                                ? invoiceVoucher.getSourceType().name()
                                : null
                )
                .voucherSourceId(
                        invoicePresent
                                ? invoiceVoucher.getSourceId()
                                : null
                )
                .voucherDate(
                        invoicePresent
                                ? invoiceVoucher.getVoucherDate()
                                : null
                )
                .totalDebit(
                        invoicePresent
                                ? invoiceVoucher.getTotalDebit()
                                : ZERO
                )
                .totalCredit(
                        invoicePresent
                                ? invoiceVoucher.getTotalCredit()
                                : ZERO
                )
                .voucherStatus(
                        invoicePresent
                                && invoiceVoucher.getStatus() != null
                                ? invoiceVoucher.getStatus().name()
                                : null
                )

                .paymentVoucherCreated(paymentPresent)
                .paymentVoucherId(
                        paymentPresent
                                ? paymentVoucher.getId()
                                : null
                )
                .paymentVoucherNumber(
                        paymentPresent
                                ? paymentVoucher.getVoucherNumber()
                                : null
                )
                .paymentVoucherDate(
                        paymentPresent
                                ? paymentVoucher.getVoucherDate()
                                : null
                )
                .paymentVoucherTotalDebit(
                        paymentPresent
                                ? paymentVoucher.getTotalDebit()
                                : ZERO
                )
                .paymentVoucherTotalCredit(
                        paymentPresent
                                ? paymentVoucher.getTotalCredit()
                                : ZERO
                )
                .paymentVoucherStatus(
                        paymentPresent
                                && paymentVoucher.getStatus() != null
                                ? paymentVoucher.getStatus().name()
                                : null
                )

                .syncStatus("SUCCESS")
                .syncedAt(LocalDateTime.now())
                .message(
                        paymentPresent
                                ? "Vendor invoice and payment posted successfully"
                                : invoicePresent
                                ? "Vendor purchase invoice posted successfully"
                                : "Vendor synchronized successfully"
                )
                .build();
    }

    // =====================================================================
    // VOUCHER / AMOUNT HELPERS
    // =====================================================================

    private AccountingVoucherResponseDto findPostedVoucherResponse(
            VoucherSourceType sourceType,
            Long sourceId
    ) {
        Optional<AccountingVoucher> existing =
                accountingVoucherRepository
                        .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                                sourceType,
                                sourceId,
                                VoucherStatus.POSTED
                        );

        return existing
                .map(value ->
                        accountingVoucherService
                                .getVoucherById(value.getId())
                )
                .orElse(null);
    }

    private AccountingVoucherEntryRequestDto debit(
            Long ledgerId,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledgerId)
                .debitAmount(money(amount))
                .creditAmount(ZERO)
                .narration(narration)
                .build();
    }

    private AccountingVoucherEntryRequestDto credit(
            Long ledgerId,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledgerId)
                .debitAmount(ZERO)
                .creditAmount(money(amount))
                .narration(narration)
                .build();
    }

    private void assertBalancedEntries(
            List<AccountingVoucherEntryRequestDto> entries,
            String voucherName
    ) {
        BigDecimal totalDebit = entries.stream()
                .map(AccountingVoucherEntryRequestDto::getDebitAmount)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);

        BigDecimal totalCredit = entries.stream()
                .map(AccountingVoucherEntryRequestDto::getCreditAmount)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ValidationException(
                    voucherName
                            + " is not balanced. Debit="
                            + totalDebit
                            + ", Credit="
                            + totalCredit,
                    "ERR_VENDOR_VOUCHER_NOT_BALANCED",
                    "entries"
            );
        }
    }

    private BigDecimal sumVoucherDebitByLedgerId(
            AccountingVoucherResponseDto voucher,
            Long ledgerId
    ) {
        if (voucher == null
                || voucher.getEntries() == null
                || ledgerId == null) {
            return ZERO;
        }

        return voucher.getEntries()
                .stream()
                .filter(entry ->
                        entry != null
                                && ledgerId.equals(entry.getLedgerId())
                )
                .map(entry -> money(entry.getDebitAmount()))
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);
    }

    private BigDecimal sumVoucherCreditByLedgerId(
            AccountingVoucherResponseDto voucher,
            Long ledgerId
    ) {
        if (voucher == null
                || voucher.getEntries() == null
                || ledgerId == null) {
            return ZERO;
        }

        return voucher.getEntries()
                .stream()
                .filter(entry ->
                        entry != null
                                && ledgerId.equals(entry.getLedgerId())
                )
                .map(entry -> money(entry.getCreditAmount()))
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);
    }

    private BigDecimal sumVoucherDebitByLedgerType(
            AccountingVoucherResponseDto voucher,
            LedgerType ledgerType
    ) {
        if (voucher == null
                || voucher.getEntries() == null
                || ledgerType == null) {
            return ZERO;
        }

        return voucher.getEntries()
                .stream()
                .filter(entry ->
                        entry != null
                                && entry.getLedgerType() == ledgerType
                )
                .map(entry -> money(entry.getDebitAmount()))
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);
    }

    private BigDecimal sumVoucherCreditByLedgerType(
            AccountingVoucherResponseDto voucher,
            LedgerType ledgerType
    ) {
        if (voucher == null
                || voucher.getEntries() == null
                || ledgerType == null) {
            return ZERO;
        }

        return voucher.getEntries()
                .stream()
                .filter(entry ->
                        entry != null
                                && entry.getLedgerType() == ledgerType
                )
                .map(entry -> money(entry.getCreditAmount()))
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);
    }

    private String buildInvoiceNarration(
            VendorPaymentApprovalRequestDto request,
            CalculatedAmounts amounts
    ) {
        String invoice = hasText(request.getInvoiceNumber())
                ? request.getInvoiceNumber().trim()
                : "payment request "
                + request.getProcurementPaymentRequestId();

        return "Procurement purchase invoice "
                + invoice
                + " | taxable="
                + amounts.price()
                + " | GST="
                + amounts.totalGstAmount()
                + " | gross="
                + amounts.grossInvoiceAmount();
    }

    private String buildPaymentNarration(
            VendorPaymentApprovalRequestDto request,
            CalculatedAmounts amounts,
            LedgerMaster bankLedger
    ) {
        StringBuilder value = new StringBuilder()
                .append("Vendor payment | gross=")
                .append(amounts.grossInvoiceAmount())
                .append(" | bank=")
                .append(amounts.vendorNetPayableAmount())
                .append(" | TDS=")
                .append(amounts.tdsAmount())
                .append(" | paymentLedger=")
                .append(bankLedger.getLedgerName());

        if (hasText(request.getPaymentMode())) {
            value.append(" | mode=")
                    .append(request.getPaymentMode().trim());
        }

        if (hasText(request.getTransactionReference())) {
            value.append(" | ref=")
                    .append(request.getTransactionReference().trim());
        }

        return value.toString();
    }

    // =====================================================================
    // GENERIC HELPERS
    // =====================================================================

    private GstRegistrationType resolveGstRegistrationType(
            String value,
            boolean required
    ) {
        if (!hasText(value)) {
            if (required) {
                throw new ValidationException(
                        "GST registration type is required for payment accounting",
                        "ERR_GST_REGISTRATION_TYPE_REQUIRED",
                        "gstRegistrationType"
                );
            }
            return null;
        }

        try {
            return GstRegistrationType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(
                    "Invalid GST registration type: " + value,
                    "ERR_INVALID_GST_REGISTRATION_TYPE",
                    "gstRegistrationType"
            );
        }
    }

    private BigDecimal requiredPositiveMoney(
            BigDecimal value,
            String message,
            String code,
            String field
    ) {
        if (value == null
                || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    message,
                    code,
                    field
            );
        }

        return money(value);
    }

    private void validatePositiveRate(
            BigDecimal value,
            String label,
            String field
    ) {
        if (value == null
                || value.compareTo(BigDecimal.ZERO) <= 0
                || value.compareTo(HUNDRED) > 0) {
            throw new ValidationException(
                    label + " must be greater than 0 and not exceed 100",
                    "ERR_INVALID_PERCENTAGE",
                    field
            );
        }
    }

    private void validateOptionalSnapshotAmount(
            String label,
            BigDecimal supplied,
            BigDecimal expected,
            String field
    ) {
        if (supplied == null) {
            return;
        }

        assertAmountEquals(
                label,
                expected,
                supplied,
                field
        );
    }

    private void assertAmountEquals(
            String label,
            BigDecimal expected,
            BigDecimal actual,
            String field
    ) {
        BigDecimal expectedMoney = money(expected);
        BigDecimal actualMoney = money(actual);

        if (expectedMoney.compareTo(actualMoney) != 0) {
            throw new ValidationException(
                    label
                            + " mismatch. Expected "
                            + expectedMoney
                            + " but received "
                            + actualMoney,
                    "ERR_ACCOUNTING_AMOUNT_MISMATCH",
                    field
            );
        }
    }

    private BigDecimal percentageAmount(
            BigDecimal amount,
            BigDecimal percentage
    ) {
        return amount.multiply(percentage)
                .divide(
                        HUNDRED,
                        MONEY_SCALE,
                        ROUNDING
                );
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return value == null
                ? ZERO
                : value.setScale(
                MONEY_SCALE,
                ROUNDING
        );
    }

    private BigDecimal rate(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO.setScale(
                RATE_SCALE,
                ROUNDING
        )
                : value.setScale(
                RATE_SCALE,
                ROUNDING
        );
    }

    private String normalizeEnum(
            String value
    ) {
        return hasText(value)
                ? value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                : null;
    }

    private String formatGroupName(
            LedgerGroupType groupType
    ) {
        return Arrays.stream(
                        groupType.name()
                                .toLowerCase(Locale.ROOT)
                                .split("_")
                )
                .map(word ->
                        word.substring(0, 1)
                                .toUpperCase(Locale.ROOT)
                                + word.substring(1)
                )
                .reduce(
                        (left, right) -> left + " " + right
                )
                .orElse(groupType.name());
    }

    private String generateVendorLedgerCode(
            Long vendorId
    ) {
        return String.format(
                Locale.ROOT,
                "VEN-%06d",
                vendorId
        );
    }

    private String clean(
            String value
    ) {
        return hasText(value)
                ? value.trim()
                : null;
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.trim().isEmpty();
    }

    private Long id(
            LedgerMaster ledger
    ) {
        return ledger != null
                ? ledger.getId()
                : null;
    }

    private record CalculatedAmounts(
            BigDecimal price,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal totalGstAmount,
            BigDecimal grossInvoiceAmount,
            BigDecimal tdsAmount,
            BigDecimal vendorNetPayableAmount
    ) {
    }

    @Builder
    private record PaymentAccountingResult(
            CalculatedAmounts amounts,
            AccountingVoucherResponseDto invoiceVoucher,
            AccountingVoucherResponseDto paymentVoucher,
            Long purchaseLedgerId,
            Long inputCgstLedgerId,
            Long inputSgstLedgerId,
            Long inputIgstLedgerId,
            Long tdsPayableLedgerId,
            Long paymentBankLedgerId
    ) {
    }
}
