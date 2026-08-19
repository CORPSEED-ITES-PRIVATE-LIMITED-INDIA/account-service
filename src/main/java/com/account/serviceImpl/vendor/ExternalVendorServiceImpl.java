package com.account.serviceImpl.vendor;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.ledger.*;
import com.account.domain.vendor.ExternalVendor;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.vendor.AccountVendorSyncRequestDto;
import com.account.dto.vendor.AccountVendorSyncResponseDto;
import com.account.dto.vendor.VendorPaymentApprovalRequestDto;
import com.account.exception.ValidationException;
import com.account.repository.ledger.AccountingVoucherRepository;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.repository.vendor.ExternalVendorRepository;
import com.account.service.ledger.AccountingVoucherService;
import com.account.service.vendor.ExternalVendorService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalVendorServiceImpl implements ExternalVendorService {

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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

    private final ExternalVendorRepository externalVendorRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AccountingVoucherService accountingVoucherService;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AccountVendorSyncResponseDto syncVendor(
            AccountVendorSyncRequestDto request
    ) {
        validateRequest(request);

        log.info(
                "Starting vendor synchronization. operationVendorId={}, " +
                        "vendorName={}, paymentApprovalPresent={}",
                request.getOperationVendorId(),
                request.getVendorName(),
                request.getPaymentApproval() != null
        );

        Optional<ExternalVendor> existingVendorOptional =
                externalVendorRepository
                        .findByOperationVendorIdAndDeletedFalse(
                                request.getOperationVendorId()
                        );

        boolean newVendor = existingVendorOptional.isEmpty();

        ExternalVendor externalVendor =
                existingVendorOptional.orElseGet(ExternalVendor::new);

        LedgerGroup sundryCreditorsGroup =
                getOrCreateLedgerGroup(
                        LedgerGroupType.SUNDRY_CREDITORS,
                        "Sundry Creditors"
                );

        LedgerMaster vendorLedger = externalVendor.getLedger();

        if (vendorLedger == null) {
            vendorLedger = createVendorLedger(
                    request,
                    sundryCreditorsGroup
            );
        } else {
            updateVendorLedger(
                    vendorLedger,
                    request,
                    sundryCreditorsGroup
            );
        }

        LedgerMaster savedVendorLedger =
                ledgerMasterRepository.saveAndFlush(vendorLedger);

        updateExternalVendor(
                externalVendor,
                request,
                savedVendorLedger
        );

        ExternalVendor savedExternalVendor =
                externalVendorRepository.saveAndFlush(externalVendor);

        PaymentAccountingResult paymentResult = null;

        if (request.getPaymentApproval() != null) {
            paymentResult = createPaymentApprovalVoucher(
                    savedExternalVendor,
                    savedVendorLedger,
                    request.getPaymentApproval()
            );
        }

        String action = newVendor ? "CREATED" : "UPDATED";

        AccountVendorSyncResponseDto response =
                buildResponse(
                        savedExternalVendor,
                        savedVendorLedger,
                        paymentResult,
                        action
                );

        log.info(
                "Vendor synchronization completed. operationVendorId={}, " +
                        "externalVendorId={}, ledgerId={}, action={}, voucherId={}",
                request.getOperationVendorId(),
                savedExternalVendor.getId(),
                savedVendorLedger.getId(),
                action,
                response.getVoucherId()
        );

        return response;
    }

    private PaymentAccountingResult createPaymentApprovalVoucher(
            ExternalVendor externalVendor,
            LedgerMaster vendorLedger,
            VendorPaymentApprovalRequestDto request
    ) {

        validatePaymentApproval(request);

        CalculatedAmounts amounts =
                calculateAmounts(request);

        /*
         * Operation sends a calculated snapshot. Account recalculates using
         * the same rules and validates every supplied calculated field.
         *
         * This catches DTO/property mapping problems immediately instead of
         * silently posting a voucher with zero GST/TDS.
         */
        validateSuppliedCalculationSnapshot(
                request,
                amounts
        );

        if (!hasPaymentReleaseData(request)) {
            throw new ValidationException(
                    "Payment release data is required",
                    "ERR_PAYMENT_RELEASE_DATA_REQUIRED",
                    "paymentApproval"
            );
        }

        validatePaymentReleaseData(
                request,
                amounts
        );

        BigDecimal bankPaymentAmount =
                money(request.getBankPaymentAmount());

        BigDecimal tdsAmount =
                money(amounts.tdsAmount());

        BigDecimal settlementAmount =
                money(
                        bankPaymentAmount.add(tdsAmount)
                );

        /*
         * Current accounting model:
         *
         * PURCHASE INVOICE
         *   Dr Purchase                taxable/basic
         *   Dr Input CGST/SGST/IGST    GST
         *       Cr Vendor              gross invoice
         *
         * PAYMENT
         *   Dr Vendor                  gross invoice
         *       Cr Bank/Cash           actual bank amount
         *       Cr TDS Payable         TDS withheld
         *
         * TDS is therefore posted ONLY in the PAYMENT voucher.
         */
        if (settlementAmount.compareTo(
                money(amounts.grossInvoiceAmount())
        ) != 0) {
            throw new ValidationException(
                    "Vendor settlement amount mismatch. Expected gross invoice amount: "
                            + money(amounts.grossInvoiceAmount())
                            + ", calculated settlement: "
                            + settlementAmount,
                    "ERR_VENDOR_SETTLEMENT_AMOUNT_MISMATCH",
                    "paymentApproval"
            );
        }

        log.info(
                "[VENDOR-ACCOUNTING-SNAPSHOT] operationVendorId={} | "
                        + "paymentRequestId={} | price={} | cgst={} | sgst={} | "
                        + "igst={} | totalGst={} | grossInvoice={} | "
                        + "tdsActive={} | tdsPercentage={} | tds={} | "
                        + "vendorNetPayable={} | bankPayment={} | settlement={}",
                externalVendor.getOperationVendorId(),
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
                amounts.vendorNetPayableAmount(),
                bankPaymentAmount,
                settlementAmount
        );

        // =========================================================
        // 1. RESOLVE ACCOUNTING LEDGERS
        // =========================================================

        LedgerMaster purchaseLedger =
                getOrCreateSystemLedger(
                        LedgerType.PURCHASE,
                        LedgerGroupType.PURCHASE_ACCOUNTS,
                        "Procurement Purchase",
                        PURCHASE_LEDGER_CODE,
                        DebitCredit.DEBIT
                );

        LedgerMaster inputCgstLedger = null;
        LedgerMaster inputSgstLedger = null;
        LedgerMaster inputIgstLedger = null;

        if (amounts.cgstAmount().compareTo(ZERO) > 0) {
            inputCgstLedger =
                    getOrCreateSystemLedger(
                            LedgerType.INPUT_CGST,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "Input CGST",
                            INPUT_CGST_LEDGER_CODE,
                            DebitCredit.DEBIT
                    );
        }

        if (amounts.sgstAmount().compareTo(ZERO) > 0) {
            inputSgstLedger =
                    getOrCreateSystemLedger(
                            LedgerType.INPUT_SGST,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "Input SGST",
                            INPUT_SGST_LEDGER_CODE,
                            DebitCredit.DEBIT
                    );
        }

        if (amounts.igstAmount().compareTo(ZERO) > 0) {
            inputIgstLedger =
                    getOrCreateSystemLedger(
                            LedgerType.INPUT_IGST,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "Input IGST",
                            INPUT_IGST_LEDGER_CODE,
                            DebitCredit.DEBIT
                    );
        }

        LedgerMaster tdsPayableLedger = null;

        if (tdsAmount.compareTo(ZERO) > 0) {
            tdsPayableLedger =
                    getOrCreateSystemLedger(
                            LedgerType.TDS_PAYABLE,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "TDS Payable",
                            TDS_PAYABLE_LEDGER_CODE,
                            DebitCredit.CREDIT
                    );
        }

        LedgerMaster bankLedger =
                getAndValidatePaymentLedger(
                        request.getBankLedgerId()
                );

        // =========================================================
        // 2. PURCHASE INVOICE VOUCHER
        // =========================================================

        AccountingVoucherResponseDto invoiceVoucher;
        boolean invoiceAlreadyPosted;

        Optional<AccountingVoucher> existingInvoiceVoucher =
                accountingVoucherRepository
                        .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                                VoucherSourceType.PROCUREMENT_VENDOR_INVOICE,
                                request.getProcurementPaymentRequestId(),
                                VoucherStatus.POSTED
                        );

        if (existingInvoiceVoucher.isPresent()) {

            invoiceVoucher =
                    accountingVoucherService.getVoucherById(
                            existingInvoiceVoucher.get().getId()
                    );

            /*
             * Important migration protection:
             * another service in the codebase can post TDS in the invoice
             * voucher. This flow posts TDS at payment time, so accepting such
             * an invoice would double-book TDS.
             */
            validateExistingInvoiceVoucherForPaymentTimeTdsModel(
                    invoiceVoucher,
                    vendorLedger,
                    amounts
            );

            invoiceAlreadyPosted = true;

        } else {

            List<AccountingVoucherEntryRequestDto> invoiceEntries =
                    new ArrayList<>();

            /*
             * DR PURCHASE / EXPENSE
             */
            invoiceEntries.add(
                    debitEntry(
                            purchaseLedger,
                            amounts.price(),
                            "Procurement purchase booked for "
                                    + resolveProcurementReference(request)
                    )
            );

            /*
             * DR INPUT GST
             */
            if (inputCgstLedger != null) {
                invoiceEntries.add(
                        debitEntry(
                                inputCgstLedger,
                                amounts.cgstAmount(),
                                "Input CGST on "
                                        + resolveProcurementReference(request)
                        )
                );
            }

            if (inputSgstLedger != null) {
                invoiceEntries.add(
                        debitEntry(
                                inputSgstLedger,
                                amounts.sgstAmount(),
                                "Input SGST on "
                                        + resolveProcurementReference(request)
                        )
                );
            }

            if (inputIgstLedger != null) {
                invoiceEntries.add(
                        debitEntry(
                                inputIgstLedger,
                                amounts.igstAmount(),
                                "Input IGST on "
                                        + resolveProcurementReference(request)
                        )
                );
            }

            /*
             * CR VENDOR = GROSS INVOICE
             *
             * Do NOT deduct TDS here. TDS is credited in the PAYMENT voucher.
             */
            invoiceEntries.add(
                    creditEntry(
                            vendorLedger,
                            amounts.grossInvoiceAmount(),
                            "Gross vendor liability booked for "
                                    + externalVendor.getVendorName()
                    )
            );

            LocalDate invoiceVoucherDate =
                    request.getInvoiceDate() != null
                            ? request.getInvoiceDate()
                            : request.getApprovedDate() != null
                            ? request.getApprovedDate()
                            : request.getPaymentDate() != null
                            ? request.getPaymentDate()
                            : LocalDate.now();

            AccountingVoucherRequestDto invoiceVoucherRequest =
                    AccountingVoucherRequestDto.builder()
                            .voucherType(
                                    VoucherType.PURCHASE_INVOICE
                            )
                            .voucherDate(
                                    invoiceVoucherDate
                            )
                            .sourceType(
                                    VoucherSourceType.PROCUREMENT_VENDOR_INVOICE
                            )
                            .sourceId(
                                    request.getProcurementPaymentRequestId()
                            )
                            .narration(
                                    buildVoucherNarration(
                                            externalVendor,
                                            request,
                                            amounts
                                    )
                            )
                            .entries(
                                    invoiceEntries
                            )
                            .build();

            invoiceVoucher =
                    accountingVoucherService.createVoucher(
                            invoiceVoucherRequest
                    );

            invoiceAlreadyPosted = false;

            log.info(
                    "[VENDOR-PURCHASE-INVOICE-POSTED] operationVendorId={} | "
                            + "paymentRequestId={} | voucherId={} | voucherNumber={} | "
                            + "price={} | cgst={} | sgst={} | igst={} | "
                            + "totalGst={} | vendorGrossCredit={}",
                    externalVendor.getOperationVendorId(),
                    request.getProcurementPaymentRequestId(),
                    invoiceVoucher.getId(),
                    invoiceVoucher.getVoucherNumber(),
                    amounts.price(),
                    amounts.cgstAmount(),
                    amounts.sgstAmount(),
                    amounts.igstAmount(),
                    amounts.totalGstAmount(),
                    amounts.grossInvoiceAmount()
            );
        }

        // =========================================================
        // 3. PAYMENT VOUCHER
        // =========================================================

        AccountingVoucherResponseDto paymentVoucher;
        boolean paymentAlreadyPosted;

        Optional<AccountingVoucher> existingPaymentVoucher =
                accountingVoucherRepository
                        .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                                VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT,
                                request.getProcurementPaymentRequestId(),
                                VoucherStatus.POSTED
                        );

        if (existingPaymentVoucher.isPresent()) {

            paymentVoucher =
                    accountingVoucherService.getVoucherById(
                            existingPaymentVoucher.get().getId()
                    );

            /*
             * Do not silently accept a legacy two-entry payment voucher when
             * TDS was expected. It must be cancelled/reversed and reposted.
             */
            validateExistingPaymentVoucher(
                    paymentVoucher,
                    vendorLedger,
                    bankLedger,
                    settlementAmount,
                    bankPaymentAmount,
                    tdsAmount
            );

            paymentAlreadyPosted = true;

        } else {

            List<AccountingVoucherEntryRequestDto> paymentEntries =
                    new ArrayList<>();

            /*
             * DR VENDOR = BANK + TDS = GROSS SETTLEMENT
             */
            paymentEntries.add(
                    debitEntry(
                            vendorLedger,
                            settlementAmount,
                            "Vendor liability settled for "
                                    + externalVendor.getVendorName()
                    )
            );

            /*
             * CR BANK / CASH = ACTUAL OUTFLOW
             */
            paymentEntries.add(
                    creditEntry(
                            bankLedger,
                            bankPaymentAmount,
                            "Vendor payment through "
                                    + displayPaymentLedgerName(bankLedger)
                    )
            );

            /*
             * CR TDS PAYABLE = AMOUNT WITHHELD
             */
            if (tdsPayableLedger != null) {
                paymentEntries.add(
                        creditEntry(
                                tdsPayableLedger,
                                tdsAmount,
                                "TDS deducted from vendor payment"
                        )
                );
            }

            LocalDate paymentVoucherDate =
                    request.getPaymentDate() != null
                            ? request.getPaymentDate()
                            : request.getPaymentReleasedDate() != null
                            ? request.getPaymentReleasedDate()
                            : LocalDate.now();

            AccountingVoucherRequestDto paymentVoucherRequest =
                    AccountingVoucherRequestDto.builder()
                            .voucherType(
                                    VoucherType.PAYMENT
                            )
                            .voucherDate(
                                    paymentVoucherDate
                            )
                            .sourceType(
                                    VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT
                            )
                            .sourceId(
                                    request.getProcurementPaymentRequestId()
                            )
                            .narration(
                                    buildPaymentVoucherNarration(
                                            externalVendor,
                                            request,
                                            settlementAmount,
                                            bankPaymentAmount,
                                            tdsAmount,
                                            bankLedger
                                    )
                            )
                            .entries(
                                    paymentEntries
                            )
                            .build();

            paymentVoucher =
                    accountingVoucherService.createVoucher(
                            paymentVoucherRequest
                    );

            paymentAlreadyPosted = false;

            log.info(
                    "[VENDOR-PAYMENT-POSTED] operationVendorId={} | "
                            + "paymentRequestId={} | voucherId={} | voucherNumber={} | "
                            + "settlement={} | bank={} | tds={} | tdsPayableLedgerId={}",
                    externalVendor.getOperationVendorId(),
                    request.getProcurementPaymentRequestId(),
                    paymentVoucher.getId(),
                    paymentVoucher.getVoucherNumber(),
                    settlementAmount,
                    bankPaymentAmount,
                    tdsAmount,
                    tdsPayableLedger != null
                            ? tdsPayableLedger.getId()
                            : null
            );
        }

        return PaymentAccountingResult.builder()
                .amounts(
                        amounts
                )
                .invoiceVoucher(
                        invoiceVoucher
                )
                .paymentVoucher(
                        paymentVoucher
                )
                .invoiceAlreadyPosted(
                        invoiceAlreadyPosted
                )
                .paymentAlreadyPosted(
                        paymentAlreadyPosted
                )
                .purchaseLedgerId(
                        purchaseLedger.getId()
                )
                .inputCgstLedgerId(
                        id(inputCgstLedger)
                )
                .inputSgstLedgerId(
                        id(inputSgstLedger)
                )
                .inputIgstLedgerId(
                        id(inputIgstLedger)
                )
                .tdsPayableLedgerId(
                        id(tdsPayableLedger)
                )
                .paymentBankLedgerId(
                        bankLedger.getId()
                )
                .build();
    }

    CalculatedAmounts calculateAmounts(
            VendorPaymentApprovalRequestDto request
    ) {
        BigDecimal price = money(request.getPrice());

        BigDecimal gstPercentage = rate(request.getGstPercentage());

        /*
         * Backward compatibility:
         * older Operation clients did not send gstActive.
         */
        boolean gstActive = request.getGstActive() != null
                ? Boolean.TRUE.equals(request.getGstActive())
                : gstPercentage.compareTo(BigDecimal.ZERO) > 0;

        GstRegistrationType registrationType =
                parsePaymentGstRegistrationType(
                        request.getGstRegistrationType()
                );

        String supplyType = normalizeEnum(request.getGstSupplyType());

        BigDecimal cgstAmount = ZERO;
        BigDecimal sgstAmount = ZERO;
        BigDecimal igstAmount = ZERO;
        BigDecimal totalGstAmount = ZERO;

        if (!gstActive) {
            if (gstPercentage.compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(
                        "GST percentage must be zero when GST is inactive",
                        "ERR_GST_PERCENTAGE_NOT_ALLOWED",
                        "paymentApproval.gstPercentage"
                );
            }
        } else {
            if (registrationType == null) {
                throw new ValidationException(
                        "GST registration type is required when GST is active",
                        "ERR_GST_REGISTRATION_TYPE_REQUIRED",
                        "paymentApproval.gstRegistrationType"
                );
            }

            if (registrationType.isZeroRated()) {
                if (gstPercentage.compareTo(BigDecimal.ZERO) != 0) {
                    throw new ValidationException(
                            "GST percentage must be zero for "
                                    + registrationType,
                            "ERR_ZERO_RATED_GST_PERCENTAGE_NOT_ALLOWED",
                            "paymentApproval.gstPercentage"
                    );
                }
            } else if (registrationType.isGstApplicable()) {
                if (gstPercentage.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ValidationException(
                            "GST percentage must be greater than zero for "
                                    + registrationType,
                            "ERR_GST_PERCENTAGE_REQUIRED",
                            "paymentApproval.gstPercentage"
                    );
                }

                totalGstAmount =
                        percentageAmount(price, gstPercentage);

                if ("INTRA_STATE".equals(supplyType)) {
                    cgstAmount =
                            totalGstAmount.divide(
                                    new BigDecimal("2"),
                                    2,
                                    RoundingMode.HALF_UP
                            );

                    sgstAmount =
                            totalGstAmount.subtract(cgstAmount)
                                    .setScale(
                                            2,
                                            RoundingMode.HALF_UP
                                    );
                } else if ("INTER_STATE".equals(supplyType)) {
                    igstAmount = totalGstAmount;
                } else {
                    throw new ValidationException(
                            "GST supply type must be INTRA_STATE or INTER_STATE",
                            "ERR_INVALID_GST_SUPPLY_TYPE",
                            "paymentApproval.gstSupplyType"
                    );
                }
            } else {
                throw new ValidationException(
                        "GST cannot be applied for registration type "
                                + registrationType,
                        "ERR_GST_NOT_ALLOWED_FOR_REGISTRATION_TYPE",
                        "paymentApproval.gstRegistrationType"
                );
            }
        }

        BigDecimal grossInvoiceAmount =
                price.add(totalGstAmount)
                        .setScale(2, RoundingMode.HALF_UP);

        boolean tdsActive = Boolean.TRUE.equals(request.getTdsActive());
        BigDecimal tdsPercentage = rate(request.getTdsPercentage());
        BigDecimal tdsAmount = ZERO;

        if (tdsActive) {
            if (tdsPercentage.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "TDS percentage must be greater than zero when TDS is active",
                        "ERR_TDS_PERCENTAGE_REQUIRED",
                        "paymentApproval.tdsPercentage"
                );
            }

            tdsAmount = percentageAmount(price, tdsPercentage);
        } else if (tdsPercentage.compareTo(BigDecimal.ZERO) != 0) {
            throw new ValidationException(
                    "TDS percentage must be zero when TDS is inactive",
                    "ERR_TDS_PERCENTAGE_NOT_ALLOWED",
                    "paymentApproval.tdsPercentage"
            );
        }

        BigDecimal vendorNetPayableAmount =
                grossInvoiceAmount.subtract(tdsAmount)
                        .setScale(2, RoundingMode.HALF_UP);

        if (vendorNetPayableAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "Vendor net payable amount cannot be negative",
                    "ERR_VENDOR_NET_PAYABLE_NEGATIVE",
                    "paymentApproval"
            );
        }

        return new CalculatedAmounts(
                price,
                cgstAmount,
                sgstAmount,
                igstAmount,
                totalGstAmount,
                grossInvoiceAmount,
                tdsAmount,
                vendorNetPayableAmount
        );
    }

    private LedgerMaster createVendorLedger(
            AccountVendorSyncRequestDto request,
            LedgerGroup sundryCreditorsGroup
    ) {
        LedgerMaster ledger = new LedgerMaster();

        ledger.setLedgerName(
                resolveLedgerName(
                        request.getVendorName(),
                        request.getOperationVendorId(),
                        null
                )
        );

        ledger.setLedgerCode(
                generateVendorLedgerCode(
                        request.getOperationVendorId()
                )
        );

        ledger.setLedgerType(LedgerType.VENDOR);
        ledger.setLedgerGroup(sundryCreditorsGroup);

        ledger.setOpeningBalance(ZERO);
        ledger.setOpeningBalanceType(DebitCredit.CREDIT);

        ledger.setCurrentBalance(ZERO);
        ledger.setCurrentBalanceType(DebitCredit.CREDIT);

        applyLedgerDetails(ledger, request);

        ledger.setSystemCreated(true);
        ledger.setActive(Boolean.TRUE.equals(request.getActive()));
        ledger.setDeleted(false);

        return ledger;
    }

    private void updateVendorLedger(
            LedgerMaster ledger,
            AccountVendorSyncRequestDto request,
            LedgerGroup sundryCreditorsGroup
    ) {
        ledger.setLedgerName(
                resolveLedgerName(
                        request.getVendorName(),
                        request.getOperationVendorId(),
                        ledger.getId()
                )
        );

        ledger.setLedgerType(LedgerType.VENDOR);
        ledger.setLedgerGroup(sundryCreditorsGroup);

        applyLedgerDetails(ledger, request);

        ledger.setSystemCreated(true);
        ledger.setActive(Boolean.TRUE.equals(request.getActive()));
        ledger.setDeleted(false);
    }

    private void applyLedgerDetails(
            LedgerMaster ledger,
            AccountVendorSyncRequestDto request
    ) {
        ledger.setGstNo(
                cleanUpperCase(request.getGstNumber())
        );

        ledger.setPanNo(
                cleanUpperCase(request.getPan())
        );

        ledger.setBankName(
                clean(request.getBankName())
        );

        ledger.setAccountHolderName(
                clean(request.getAccountHolderName())
        );

        ledger.setAccountNumber(
                clean(request.getBankAccountNumber())
        );

        ledger.setIfscCode(
                cleanUpperCase(request.getIfscCode())
        );

        ledger.setBranchName(
                limit(
                        clean(request.getBranchAddress()),
                        100
                )
        );
    }

    private void updateExternalVendor(
            ExternalVendor externalVendor,
            AccountVendorSyncRequestDto request,
            LedgerMaster ledger
    ) {
        externalVendor.setOperationVendorId(
                request.getOperationVendorId()
        );

        externalVendor.setVendorAccountsSubmissionId(
                request.getVendorAccountsSubmissionId()
        );

        externalVendor.setVendorFinalizationId(
                request.getVendorFinalizationId()
        );

        externalVendor.setLedger(ledger);

        externalVendor.setVendorName(
                normalizeName(request.getVendorName())
        );

        externalVendor.setEmail(
                cleanLowerCase(request.getEmail())
        );

        externalVendor.setMobile(
                clean(request.getMobile())
        );

        externalVendor.setPanNumber(
                cleanUpperCase(request.getPan())
        );

        externalVendor.setGstNumber(
                cleanUpperCase(request.getGstNumber())
        );

        externalVendor.setGstRegistrationType(
                parseGstRegistrationType(
                        request.getGstRegistrationType()
                )
        );

        externalVendor.setAccountHolderName(
                clean(request.getAccountHolderName())
        );

        externalVendor.setBankAccountNumber(
                clean(request.getBankAccountNumber())
        );

        externalVendor.setIfscCode(
                cleanUpperCase(request.getIfscCode())
        );

        externalVendor.setBankName(
                clean(request.getBankName())
        );

        externalVendor.setBranchAddress(
                clean(request.getBranchAddress())
        );

        externalVendor.setFullAddress(
                clean(request.getFullAddress())
        );

        externalVendor.setCity(
                clean(request.getCity())
        );

        externalVendor.setState(
                clean(request.getState())
        );

        externalVendor.setCountry(
                clean(request.getCountry())
        );

        externalVendor.setApprovedByOperationUserId(
                request.getApprovedByOperationUserId()
        );

        externalVendor.setApprovedAt(
                request.getApprovedAt()
        );

        externalVendor.setOperationUpdatedAt(
                request.getOperationUpdatedAt()
        );

        externalVendor.setLastSyncedAt(
                LocalDateTime.now()
        );

        externalVendor.setActive(
                Boolean.TRUE.equals(request.getActive())
        );

        externalVendor.setDeleted(false);
    }

    private LedgerMaster getOrCreateSystemLedger(
            LedgerType ledgerType,
            LedgerGroupType groupType,
            String ledgerName,
            String ledgerCode,
            DebitCredit normalBalanceType
    ) {
        Optional<LedgerMaster> byType =
                ledgerMasterRepository
                        .findByLedgerTypeAndDeletedFalse(
                                ledgerType
                        );

        if (byType.isPresent()) {
            LedgerMaster existing = byType.get();

            if (!existing.isActive()) {
                existing.setActive(true);

                return ledgerMasterRepository.save(existing);
            }

            return existing;
        }

        Optional<LedgerMaster> byCode =
                ledgerMasterRepository
                        .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                                ledgerCode
                        );

        if (byCode.isPresent()) {
            return byCode.get();
        }

        LedgerGroup ledgerGroup =
                getOrCreateLedgerGroup(
                        groupType,
                        formatGroupName(groupType)
                );

        LedgerMaster ledger = new LedgerMaster();

        ledger.setLedgerName(ledgerName);
        ledger.setLedgerCode(ledgerCode);
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setOpeningBalance(ZERO);
        ledger.setOpeningBalanceType(normalBalanceType);

        ledger.setCurrentBalance(ZERO);
        ledger.setCurrentBalanceType(normalBalanceType);

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
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerGroup getOrCreateLedgerGroup(
            LedgerGroupType groupType,
            String groupName
    ) {
        return ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(groupType)
                .map(existingGroup -> {
                    if (!existingGroup.isActive()) {
                        existingGroup.setActive(true);
                        return ledgerGroupRepository.save(existingGroup);
                    }

                    return existingGroup;
                })
                .orElseGet(() -> {
                    LedgerGroup group =
                            LedgerGroup.builder()
                                    .name(groupName)
                                    .groupType(groupType)
                                    .description(
                                            "System-created ledger group"
                                    )
                                    .systemDefault(true)
                                    .active(true)
                                    .deleted(false)
                                    .build();

                    return ledgerGroupRepository.save(group);
                });
    }

    private AccountVendorSyncResponseDto buildResponse(
            ExternalVendor externalVendor,
            LedgerMaster ledger,
            PaymentAccountingResult paymentResult,
            String action
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

        boolean invoiceVoucherCreated = invoiceVoucher != null;
        boolean paymentVoucherCreated = paymentVoucher != null;

        return AccountVendorSyncResponseDto.builder()
                .externalVendorId(externalVendor.getId())
                .operationVendorId(
                        externalVendor.getOperationVendorId()
                )
                .vendorAccountsSubmissionId(
                        externalVendor.getVendorAccountsSubmissionId()
                )
                .vendorFinalizationId(
                        externalVendor.getVendorFinalizationId()
                )
                .vendorName(externalVendor.getVendorName())

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

                .action(action)
                .active(externalVendor.isActive())

                .price(amounts != null ? amounts.price() : null)
                .cgstAmount(amounts != null ? amounts.cgstAmount() : null)
                .sgstAmount(amounts != null ? amounts.sgstAmount() : null)
                .igstAmount(amounts != null ? amounts.igstAmount() : null)
                .totalGstAmount(
                        amounts != null ? amounts.totalGstAmount() : null
                )
                .grossInvoiceAmount(
                        amounts != null ? amounts.grossInvoiceAmount() : null
                )
                .tdsAmount(amounts != null ? amounts.tdsAmount() : null)
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

                /*
                 * Legacy voucher fields continue to represent
                 * the PURCHASE_INVOICE voucher.
                 */
                .voucherCreated(invoiceVoucherCreated)
                .voucherId(
                        invoiceVoucherCreated
                                ? invoiceVoucher.getId()
                                : null
                )
                .voucherNumber(
                        invoiceVoucherCreated
                                ? invoiceVoucher.getVoucherNumber()
                                : null
                )
                .voucherType(
                        invoiceVoucherCreated
                                && invoiceVoucher.getVoucherType() != null
                                ? invoiceVoucher.getVoucherType().name()
                                : null
                )
                .voucherSourceType(
                        invoiceVoucherCreated
                                && invoiceVoucher.getSourceType() != null
                                ? invoiceVoucher.getSourceType().name()
                                : null
                )
                .voucherSourceId(
                        invoiceVoucherCreated
                                ? invoiceVoucher.getSourceId()
                                : null
                )
                .voucherDate(
                        invoiceVoucherCreated
                                ? invoiceVoucher.getVoucherDate()
                                : null
                )
                .totalDebit(
                        invoiceVoucherCreated
                                ? invoiceVoucher.getTotalDebit()
                                : ZERO
                )
                .totalCredit(
                        invoiceVoucherCreated
                                ? invoiceVoucher.getTotalCredit()
                                : ZERO
                )
                .voucherStatus(
                        invoiceVoucherCreated
                                && invoiceVoucher.getStatus() != null
                                ? invoiceVoucher.getStatus().name()
                                : null
                )

                .paymentVoucherCreated(paymentVoucherCreated)
                .paymentVoucherId(
                        paymentVoucherCreated
                                ? paymentVoucher.getId()
                                : null
                )
                .paymentVoucherNumber(
                        paymentVoucherCreated
                                ? paymentVoucher.getVoucherNumber()
                                : null
                )
                .paymentVoucherDate(
                        paymentVoucherCreated
                                ? paymentVoucher.getVoucherDate()
                                : null
                )
                .paymentVoucherTotalDebit(
                        paymentVoucherCreated
                                ? paymentVoucher.getTotalDebit()
                                : ZERO
                )
                .paymentVoucherTotalCredit(
                        paymentVoucherCreated
                                ? paymentVoucher.getTotalCredit()
                                : ZERO
                )
                .paymentVoucherStatus(
                        paymentVoucherCreated
                                && paymentVoucher.getStatus() != null
                                ? paymentVoucher.getStatus().name()
                                : null
                )
                .paymentBankLedgerId(
                        paymentResult != null
                                ? paymentResult.paymentBankLedgerId()
                                : null
                )

                .syncStatus("SUCCESS")
                .syncedAt(externalVendor.getLastSyncedAt())
                .message(
                        buildSyncMessage(
                                paymentResult,
                                invoiceVoucherCreated,
                                paymentVoucherCreated
                        )
                )
                .build();
    }

    private void validateRequest(
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

        if (hasText(request.getGstNumber())
                && request.getGstNumber().trim().length() != 15) {

            throw new ValidationException(
                    "GST number must contain exactly 15 characters",
                    "ERR_INVALID_VENDOR_GST_LENGTH",
                    "gstNumber"
            );
        }

        parseGstRegistrationType(
                request.getGstRegistrationType()
        );

        if (request.getPaymentApproval() != null) {
            validatePaymentApproval(
                    request.getPaymentApproval()
            );
        }
    }

    private void validatePaymentApproval(
            VendorPaymentApprovalRequestDto request
    ) {
        if (request.getProcurementPaymentRequestId() == null
                || request.getProcurementPaymentRequestId() <= 0) {

            throw new ValidationException(
                    "Valid procurement payment request ID is required",
                    "ERR_PAYMENT_REQUEST_ID_REQUIRED",
                    "paymentApproval.procurementPaymentRequestId"
            );
        }

        if (request.getPrice() == null
                || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {

            throw new ValidationException(
                    "Price must be greater than zero",
                    "ERR_INVALID_VENDOR_PRICE",
                    "paymentApproval.price"
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

        if (hasText(request.getGstRegistrationType())) {
            parsePaymentGstRegistrationType(
                    request.getGstRegistrationType()
            );
        }
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

    void validatePaymentReleaseData(
            VendorPaymentApprovalRequestDto request,
            CalculatedAmounts amounts
    ) {
        if (request.getBankPaymentAmount() == null
                || request.getBankPaymentAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new ValidationException(
                    "Bank payment amount must be greater than zero",
                    "ERR_INVALID_BANK_PAYMENT_AMOUNT",
                    "paymentApproval.bankPaymentAmount"
            );
        }

        if (request.getBankLedgerId() == null
                || request.getBankLedgerId() <= 0) {

            throw new ValidationException(
                    "Bank/Cash ledger ID is required",
                    "ERR_BANK_LEDGER_REQUIRED",
                    "paymentApproval.bankLedgerId"
            );
        }

        BigDecimal paidAmount = money(request.getBankPaymentAmount());

        /*
         * Operation Service currently models one full settlement per payment
         * request. Account Service also allows only one posted PAYMENT voucher
         * for this source ID. Therefore a smaller amount must not be accepted,
         * otherwise the residual vendor balance could never be settled through
         * the same payment request.
         */
        if (paidAmount.compareTo(amounts.vendorNetPayableAmount()) != 0) {
            throw new ValidationException(
                    "Bank payment amount mismatch. Expected full vendor net payable: "
                            + amounts.vendorNetPayableAmount()
                            + ", received: " + paidAmount,
                    "ERR_BANK_PAYMENT_AMOUNT_MISMATCH",
                    "paymentApproval.bankPaymentAmount"
            );
        }

        /*
         * TDS supplied by Operation Service is only a cross-check snapshot.
         * Account Service recalculates it from taxable price and rate, normalizes
         * both values to two decimals, and requires exact equality.
         */
        if (request.getTdsAmount() != null) {
            BigDecimal suppliedTdsAmount = money(request.getTdsAmount());

            if (suppliedTdsAmount.compareTo(amounts.tdsAmount()) != 0) {
                throw new ValidationException(
                        "Supplied TDS amount does not match Account Service calculation. "
                                + "Supplied: " + suppliedTdsAmount
                                + ", calculated: " + amounts.tdsAmount(),
                        "ERR_TDS_AMOUNT_MISMATCH",
                        "paymentApproval.tdsAmount"
                );
            }
        }
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
                "TDS base amount",
                request.getTdsBaseAmount(),
                amounts.price(),
                "paymentApproval.tdsBaseAmount"
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

        validateOptionalSnapshotAmount(
                "settlement amount",
                request.getSettlementAmount(),
                amounts.grossInvoiceAmount(),
                "paymentApproval.settlementAmount"
        );
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

        BigDecimal normalizedSupplied =
                money(supplied);

        BigDecimal normalizedExpected =
                money(expected);

        if (normalizedSupplied.compareTo(normalizedExpected) != 0) {
            throw new ValidationException(
                    "Operation/Account " + label + " mismatch. Supplied: "
                            + normalizedSupplied
                            + ", calculated: "
                            + normalizedExpected,
                    "ERR_VENDOR_ACCOUNTING_SNAPSHOT_MISMATCH",
                    field
            );
        }
    }

    private void validateExistingInvoiceVoucherForPaymentTimeTdsModel(
            AccountingVoucherResponseDto voucher,
            LedgerMaster vendorLedger,
            CalculatedAmounts amounts
    ) {
        if (voucher == null) {
            throw new ValidationException(
                    "Existing purchase invoice voucher could not be loaded",
                    "ERR_EXISTING_VENDOR_INVOICE_NOT_READABLE",
                    "paymentApproval"
            );
        }

        BigDecimal vendorCredit =
                sumVoucherCreditByLedgerId(
                        voucher,
                        vendorLedger.getId()
                );

        BigDecimal tdsCredit =
                sumVoucherCreditByLedgerType(
                        voucher,
                        LedgerType.TDS_PAYABLE
                );

        if (tdsCredit.compareTo(ZERO) > 0) {
            throw new ValidationException(
                    "Existing purchase invoice voucher already contains TDS Payable. "
                            + "This flow posts TDS at payment time; cancel/reverse the old "
                            + "invoice/payment accounting before reposting.",
                    "ERR_EXISTING_INVOICE_TDS_MODEL_CONFLICT",
                    "paymentApproval"
            );
        }

        if (vendorCredit.compareTo(
                money(amounts.grossInvoiceAmount())
        ) != 0) {
            throw new ValidationException(
                    "Existing purchase invoice vendor credit does not match gross invoice. "
                            + "Existing vendor credit: " + vendorCredit
                            + ", expected gross invoice: "
                            + money(amounts.grossInvoiceAmount())
                            + ". Cancel/reverse and repost the voucher.",
                    "ERR_EXISTING_VENDOR_INVOICE_AMOUNT_MISMATCH",
                    "paymentApproval"
            );
        }
    }

    private void validateExistingPaymentVoucher(
            AccountingVoucherResponseDto voucher,
            LedgerMaster vendorLedger,
            LedgerMaster bankLedger,
            BigDecimal expectedVendorDebit,
            BigDecimal expectedBankCredit,
            BigDecimal expectedTdsCredit
    ) {
        if (voucher == null) {
            throw new ValidationException(
                    "Existing payment voucher could not be loaded",
                    "ERR_EXISTING_VENDOR_PAYMENT_NOT_READABLE",
                    "paymentApproval"
            );
        }

        BigDecimal vendorDebit =
                sumVoucherDebitByLedgerId(
                        voucher,
                        vendorLedger.getId()
                );

        BigDecimal bankCredit =
                sumVoucherCreditByLedgerId(
                        voucher,
                        bankLedger.getId()
                );

        BigDecimal tdsCredit =
                sumVoucherCreditByLedgerType(
                        voucher,
                        LedgerType.TDS_PAYABLE
                );

        if (vendorDebit.compareTo(
                money(expectedVendorDebit)
        ) != 0
                || bankCredit.compareTo(
                money(expectedBankCredit)
        ) != 0
                || tdsCredit.compareTo(
                money(expectedTdsCredit)
        ) != 0) {

            throw new ValidationException(
                    "Existing vendor payment voucher does not match the current "
                            + "Bank + TDS accounting model. Existing Vendor Dr="
                            + vendorDebit
                            + ", Bank Cr=" + bankCredit
                            + ", TDS Cr=" + tdsCredit
                            + "; expected Vendor Dr="
                            + money(expectedVendorDebit)
                            + ", Bank Cr=" + money(expectedBankCredit)
                            + ", TDS Cr=" + money(expectedTdsCredit)
                            + ". Cancel/reverse the old voucher and repost.",
                    "ERR_EXISTING_VENDOR_PAYMENT_ACCOUNTING_MISMATCH",
                    "paymentApproval"
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
                .map(entry ->
                        money(entry.getDebitAmount())
                )
                .reduce(
                        ZERO,
                        BigDecimal::add
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
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
                .map(entry ->
                        money(entry.getCreditAmount())
                )
                .reduce(
                        ZERO,
                        BigDecimal::add
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
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
                .map(entry ->
                        money(entry.getCreditAmount())
                )
                .reduce(
                        ZERO,
                        BigDecimal::add
                )
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private LedgerMaster getAndValidatePaymentLedger(
            Long bankLedgerId
    ) {
        LedgerMaster bankLedger =
                ledgerMasterRepository
                        .findByIdAndDeletedFalse(bankLedgerId)
                        .orElseThrow(() ->
                                new ValidationException(
                                        "Bank/Cash ledger not found with ID: "
                                                + bankLedgerId,
                                        "ERR_BANK_LEDGER_NOT_FOUND",
                                        "paymentApproval.bankLedgerId"
                                )
                        );

        if (!bankLedger.isActive()) {
            throw new ValidationException(
                    "Selected Bank/Cash ledger is inactive",
                    "ERR_BANK_LEDGER_INACTIVE",
                    "paymentApproval.bankLedgerId"
            );
        }

        LedgerType type = bankLedger.getLedgerType();

        if (type != LedgerType.BANK
                && type != LedgerType.CASH
                && type != LedgerType.PAYMENT_GATEWAY) {

            throw new ValidationException(
                    "Selected ledger must be BANK, CASH or PAYMENT_GATEWAY",
                    "ERR_INVALID_PAYMENT_LEDGER",
                    "paymentApproval.bankLedgerId"
            );
        }

        return bankLedger;
    }

    private String displayPaymentLedgerName(
            LedgerMaster ledger
    ) {
        if (ledger == null) {
            return "Bank/Cash";
        }

        if (hasText(ledger.getBankName())) {
            return ledger.getBankName().trim();
        }

        return ledger.getLedgerName();
    }

    private String buildPaymentVoucherNarration(
            ExternalVendor vendor,
            VendorPaymentApprovalRequestDto request,
            BigDecimal settlementAmount,
            BigDecimal bankPaymentAmount,
            BigDecimal tdsAmount,
            LedgerMaster bankLedger
    ) {
        StringBuilder narration = new StringBuilder()
                .append("Vendor payment settled for ")
                .append(vendor.getVendorName())
                .append(", settlement ")
                .append(settlementAmount)
                .append(", bank payment ")
                .append(bankPaymentAmount)
                .append(", through ")
                .append(displayPaymentLedgerName(bankLedger));

        if (tdsAmount != null
                && tdsAmount.compareTo(ZERO) > 0) {
            narration.append(", TDS withheld ")
                    .append(tdsAmount);
        }

        if (hasText(request.getPaymentMode())) {
            narration.append(", mode ")
                    .append(request.getPaymentMode().trim());
        }

        if (hasText(request.getTransactionReference())) {
            narration.append(", reference ")
                    .append(request.getTransactionReference().trim());
        }

        return narration.toString();
    }

    private String buildSyncMessage(
            PaymentAccountingResult paymentResult,
            boolean invoiceVoucherCreated,
            boolean paymentVoucherCreated
    ) {
        if (paymentResult == null) {
            return "Vendor and vendor ledger synchronized successfully";
        }

        if (paymentResult.invoiceAlreadyPosted()
                && (!paymentVoucherCreated
                || paymentResult.paymentAlreadyPosted())) {

            return "Vendor synchronized; accounting vouchers were already posted";
        }

        if (invoiceVoucherCreated && paymentVoucherCreated) {
            return "Vendor, purchase invoice voucher and payment voucher synchronized successfully";
        }

        if (paymentVoucherCreated) {
            return "Vendor and payment voucher synchronized successfully";
        }

        if (invoiceVoucherCreated) {
            return "Vendor and purchase invoice voucher synchronized successfully";
        }

        return "Vendor synchronized successfully";
    }

    private AccountingVoucherEntryRequestDto debitEntry(
            LedgerMaster ledger,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledger.getId())
                .debitAmount(money(amount))
                .creditAmount(ZERO)
                .narration(narration)
                .build();
    }

    private AccountingVoucherEntryRequestDto creditEntry(
            LedgerMaster ledger,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledger.getId())
                .debitAmount(ZERO)
                .creditAmount(money(amount))
                .narration(narration)
                .build();
    }

    private String buildVoucherNarration(
            ExternalVendor vendor,
            VendorPaymentApprovalRequestDto request,
            CalculatedAmounts amounts
    ) {
        return "Procurement purchase approved for "
                + vendor.getVendorName()
                + ", "
                + resolveProcurementReference(request)
                + ", gross amount "
                + amounts.grossInvoiceAmount()
                + ", TDS "
                + amounts.tdsAmount()
                + ", vendor net payable "
                + amounts.vendorNetPayableAmount();
    }

    private String resolveProcurementReference(
            VendorPaymentApprovalRequestDto request
    ) {
        if (hasText(request.getPurchaseOrderNumber())) {
            return "PO " + request.getPurchaseOrderNumber().trim();
        }

        if (request.getProcurementOrderId() != null) {
            return "procurement order "
                    + request.getProcurementOrderId();
        }

        return "payment request "
                + request.getProcurementPaymentRequestId();
    }

    private String resolveLedgerName(
            String vendorName,
            Long operationVendorId,
            Long existingLedgerId
    ) {
        String normalizedName = normalizeName(vendorName);

        boolean duplicateLedgerName;

        if (existingLedgerId == null) {
            duplicateLedgerName =
                    ledgerMasterRepository
                            .existsByLedgerNameIgnoreCase(
                                    normalizedName
                            );
        } else {
            duplicateLedgerName =
                    ledgerMasterRepository
                            .existsByLedgerNameIgnoreCaseAndIdNot(
                                    normalizedName,
                                    existingLedgerId
                            );
        }

        if (duplicateLedgerName) {
            return normalizedName
                    + " - Vendor-"
                    + operationVendorId;
        }

        return normalizedName;
    }

    private String generateVendorLedgerCode(
            Long operationVendorId
    ) {
        String baseCode =
                String.format(
                        "LED-VEN-%06d",
                        operationVendorId
                );

        if (!ledgerMasterRepository
                .existsByLedgerCodeIgnoreCase(baseCode)) {

            return baseCode;
        }

        int counter = 1;
        String generatedCode;

        do {
            generatedCode =
                    baseCode + "-" + counter++;

        } while (
                ledgerMasterRepository
                        .existsByLedgerCodeIgnoreCase(
                                generatedCode
                        )
        );

        return generatedCode;
    }

    private GstRegistrationType parseGstRegistrationType(
            String value
    ) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return GstRegistrationType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException exception) {
            throw new ValidationException(
                    "Invalid GST registration type: "
                            + value,
                    "ERR_INVALID_GST_REGISTRATION_TYPE",
                    "gstRegistrationType"
            );
        }
    }

    private GstRegistrationType parsePaymentGstRegistrationType(
            String value
    ) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return GstRegistrationType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException exception) {
            throw new ValidationException(
                    "Invalid payment GST registration type: "
                            + value
                            + ". Allowed values are REGISTERED, "
                            + "UNREGISTERED, SEZ and INTERNATIONAL",
                    "ERR_INVALID_PAYMENT_GST_REGISTRATION_TYPE",
                    "paymentApproval.gstRegistrationType"
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
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal money(
            BigDecimal value
    ) {
        return value == null
                ? ZERO
                : value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal rate(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO.setScale(
                4,
                RoundingMode.HALF_UP
        )
                : value.setScale(
                4,
                RoundingMode.HALF_UP
        );
    }

    private String normalizeEnum(
            String value
    ) {
        return hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
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
                        (left, right) ->
                                left + " " + right
                )
                .orElse(groupType.name());
    }

    private String normalizeName(
            String value
    ) {
        return value == null
                ? null
                : value.trim()
                .replaceAll("\\s+", " ");
    }

    private String clean(
            String value
    ) {
        return !hasText(value)
                ? null
                : value.trim();
    }

    private String cleanUpperCase(
            String value
    ) {
        String cleaned = clean(value);

        return cleaned == null
                ? null
                : cleaned.toUpperCase(Locale.ROOT);
    }

    private String cleanLowerCase(
            String value
    ) {
        String cleaned = clean(value);

        return cleaned == null
                ? null
                : cleaned.toLowerCase(Locale.ROOT);
    }

    private String limit(
            String value,
            int maximumLength
    ) {
        if (value == null) {
            return null;
        }

        return value.length() <= maximumLength
                ? value
                : value.substring(
                0,
                maximumLength
        );
    }

    private Long id(
            LedgerMaster ledger
    ) {
        return ledger != null
                ? ledger.getId()
                : null;
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.trim().isEmpty();
    }

    record CalculatedAmounts(
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
            boolean invoiceAlreadyPosted,
            boolean paymentAlreadyPosted,
            Long purchaseLedgerId,
            Long inputCgstLedgerId,
            Long inputSgstLedgerId,
            Long inputIgstLedgerId,
            Long tdsPayableLedgerId,
            Long paymentBankLedgerId
    ) {
    }
}
