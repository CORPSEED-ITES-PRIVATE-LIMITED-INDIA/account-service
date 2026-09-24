package com.account.serviceImpl.vendor;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.ledger.*;
import com.account.domain.vendor.ExternalVendor;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.vendor.VendorPaymentApprovalAccountingResult;
import com.account.dto.vendor.VendorPaymentApprovalRequestDto;
import com.account.exception.ValidationException;
import com.account.repository.ledger.AccountingVoucherRepository;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.ledger.AccountingVoucherService;
import com.account.service.vendor.VendorPaymentApprovalAccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorPaymentApprovalAccountingServiceImpl
        implements VendorPaymentApprovalAccountingService {

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

    private final AccountingVoucherRepository accountingVoucherRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final AccountingVoucherService accountingVoucherService;

    @Override
    @Transactional
    public VendorPaymentApprovalAccountingResult postApprovalVoucher(
            ExternalVendor externalVendor,
            LedgerMaster vendorLedger,
            VendorPaymentApprovalRequestDto request
    ) {
        validateRequest(
                externalVendor,
                vendorLedger,
                request
        );

        CalculatedAmounts amounts =
                calculate(request);

        Optional<AccountingVoucher> existingVoucher =
                accountingVoucherRepository
                        .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                                VoucherSourceType.PROCUREMENT_VENDOR_INVOICE,
                                request.getProcurementPaymentRequestId(),
                                VoucherStatus.POSTED
                        );

        if (existingVoucher.isPresent()) {
            AccountingVoucherResponseDto existingResponse =
                    accountingVoucherService.getVoucherById(
                            existingVoucher.get().getId()
                    );

            return buildResult(
                    amounts,
                    existingResponse,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

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
        LedgerMaster tdsPayableLedger = null;

        List<AccountingVoucherEntryRequestDto> entries =
                new ArrayList<>();

        entries.add(
                debitEntry(
                        purchaseLedger,
                        amounts.price(),
                        "Procurement purchase booked for "
                                + safeInvoiceReference(request)
                )
        );

        if (amounts.cgstAmount()
                .compareTo(BigDecimal.ZERO) > 0) {

            inputCgstLedger =
                    getOrCreateSystemLedger(
                            LedgerType.INPUT_CGST,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "Input CGST",
                            INPUT_CGST_LEDGER_CODE,
                            DebitCredit.DEBIT
                    );

            entries.add(
                    debitEntry(
                            inputCgstLedger,
                            amounts.cgstAmount(),
                            "Input CGST on "
                                    + safeInvoiceReference(request)
                    )
            );
        }

        if (amounts.sgstAmount()
                .compareTo(BigDecimal.ZERO) > 0) {

            inputSgstLedger =
                    getOrCreateSystemLedger(
                            LedgerType.INPUT_SGST,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "Input SGST",
                            INPUT_SGST_LEDGER_CODE,
                            DebitCredit.DEBIT
                    );

            entries.add(
                    debitEntry(
                            inputSgstLedger,
                            amounts.sgstAmount(),
                            "Input SGST on "
                                    + safeInvoiceReference(request)
                    )
            );
        }

        if (amounts.igstAmount()
                .compareTo(BigDecimal.ZERO) > 0) {

            inputIgstLedger =
                    getOrCreateSystemLedger(
                            LedgerType.INPUT_IGST,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "Input IGST",
                            INPUT_IGST_LEDGER_CODE,
                            DebitCredit.DEBIT
                    );

            entries.add(
                    debitEntry(
                            inputIgstLedger,
                            amounts.igstAmount(),
                            "Input IGST on "
                                    + safeInvoiceReference(request)
                    )
            );
        }

        /*
         * Purchase/Input GST Dr
         *      To Vendor Ledger
         *      To TDS Payable
         */
        entries.add(
                creditEntry(
                        vendorLedger,
                        amounts.vendorNetPayableAmount(),
                        "Net amount payable to vendor "
                                + externalVendor.getVendorName()
                )
        );

        if (amounts.tdsAmount()
                .compareTo(BigDecimal.ZERO) > 0) {

            tdsPayableLedger =
                    getOrCreateSystemLedger(
                            LedgerType.TDS_PAYABLE,
                            LedgerGroupType.DUTIES_AND_TAXES,
                            "TDS Payable",
                            TDS_PAYABLE_LEDGER_CODE,
                            DebitCredit.CREDIT
                    );

            entries.add(
                    creditEntry(
                            tdsPayableLedger,
                            amounts.tdsAmount(),
                            "TDS deducted on vendor invoice "
                                    + safeInvoiceReference(request)
                    )
            );
        }

        LocalDate voucherDate =
                request.getInvoiceDate() != null
                        ? request.getInvoiceDate()
                        : request.getApprovedDate() != null
                        ? request.getApprovedDate()
                        : LocalDate.now();

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(
                                VoucherType.PURCHASE_INVOICE
                        )
                        .voucherDate(voucherDate)
                        .sourceType(
                                VoucherSourceType
                                        .PROCUREMENT_VENDOR_INVOICE
                        )
                        .sourceId(
                                request.getProcurementPaymentRequestId()
                        )
                        .narration(
                                buildNarration(
                                        externalVendor,
                                        request,
                                        amounts
                                )
                        )
                        .entries(entries)
                        .build();

        AccountingVoucherResponseDto voucherResponse =
                accountingVoucherService.createVoucher(
                        voucherRequest
                );

        log.info(
                "Vendor approval voucher posted. paymentRequestId={}, "
                        + "vendorId={}, voucherId={}, voucherNumber={}, "
                        + "totalDebit={}, totalCredit={}",
                request.getProcurementPaymentRequestId(),
                externalVendor.getId(),
                voucherResponse.getId(),
                voucherResponse.getVoucherNumber(),
                voucherResponse.getTotalDebit(),
                voucherResponse.getTotalCredit()
        );

        return buildResult(
                amounts,
                voucherResponse,
                false,
                purchaseLedger,
                inputCgstLedger,
                inputSgstLedger,
                inputIgstLedger,
                tdsPayableLedger
        );
    }

    private CalculatedAmounts calculate(
            VendorPaymentApprovalRequestDto request
    ) {
        BigDecimal price =
                money(request.getPrice());

        GstRegistrationType registrationType =
                parsePaymentGstRegistrationType(
                        request.getGstRegistrationType()
                );

        String supplyType =
                normalizeEnum(
                        request.getGstSupplyType()
                );

        BigDecimal gstPercentage =
                percentage(
                        request.getGstPercentage()
                );

        BigDecimal totalGst = zero();
        BigDecimal cgst = zero();
        BigDecimal sgst = zero();
        BigDecimal igst = zero();

        /*
         * SEZ and INTERNATIONAL are zero-rated.
         */
        if (registrationType != null
                && registrationType.isZeroRated()) {

            if (gstPercentage.compareTo(
                    BigDecimal.ZERO
            ) != 0) {

                throw new ValidationException(
                        "GST percentage must be zero for "
                                + registrationType,
                        "ERR_ZERO_RATED_GST_PERCENTAGE_NOT_ALLOWED",
                        "paymentApproval.gstPercentage"
                );
            }

        } else if (registrationType != null
                && registrationType.isGstApplicable()) {

            if (gstPercentage.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new ValidationException(
                        "GST percentage must be greater than zero for "
                                + registrationType,
                        "ERR_GST_PERCENTAGE_REQUIRED",
                        "paymentApproval.gstPercentage"
                );
            }

            totalGst =
                    percentageAmount(
                            price,
                            gstPercentage
                    );

            if ("INTRA_STATE".equals(supplyType)) {
                cgst =
                        totalGst.divide(
                                new BigDecimal("2"),
                                2,
                                RoundingMode.HALF_UP
                        );

                sgst =
                        totalGst.subtract(cgst)
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                );

            } else if ("INTER_STATE".equals(supplyType)) {
                igst = totalGst;

            } else {
                throw new ValidationException(
                        "GST supply type must be INTRA_STATE "
                                + "or INTER_STATE for "
                                + registrationType,
                        "ERR_INVALID_GST_SUPPLY_TYPE",
                        "paymentApproval.gstSupplyType"
                );
            }

        } else if (gstPercentage.compareTo(
                BigDecimal.ZERO
        ) != 0) {

            throw new ValidationException(
                    "GST registration type is required "
                            + "when GST percentage is provided",
                    "ERR_GST_REGISTRATION_TYPE_REQUIRED",
                    "paymentApproval.gstRegistrationType"
            );
        }

        BigDecimal grossInvoiceAmount =
                price.add(totalGst)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        boolean tdsActive =
                Boolean.TRUE.equals(
                        request.getTdsActive()
                );

        BigDecimal tdsPercentage =
                percentage(
                        request.getTdsPercentage()
                );

        BigDecimal tdsAmount = zero();

        if (tdsActive) {
            if (tdsPercentage.compareTo(
                    BigDecimal.ZERO
            ) <= 0) {

                throw new ValidationException(
                        "TDS percentage must be greater than zero "
                                + "when TDS is active",
                        "ERR_TDS_PERCENTAGE_REQUIRED",
                        "paymentApproval.tdsPercentage"
                );
            }

            /*
             * TDS is calculated on basic price,
             * excluding GST.
             */
            tdsAmount =
                    percentageAmount(
                            price,
                            tdsPercentage
                    );

        } else if (tdsPercentage.compareTo(
                BigDecimal.ZERO
        ) != 0) {

            throw new ValidationException(
                    "TDS percentage must be zero "
                            + "when TDS is inactive",
                    "ERR_TDS_PERCENTAGE_NOT_ALLOWED",
                    "paymentApproval.tdsPercentage"
            );
        }

        BigDecimal netPayable =
                grossInvoiceAmount.subtract(
                        tdsAmount
                ).setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        if (netPayable.compareTo(
                BigDecimal.ZERO
        ) < 0) {

            throw new ValidationException(
                    "Vendor net payable amount cannot be negative",
                    "ERR_VENDOR_NET_PAYABLE_NEGATIVE",
                    "paymentApproval"
            );
        }

        return new CalculatedAmounts(
                price,
                cgst,
                sgst,
                igst,
                totalGst,
                grossInvoiceAmount,
                tdsAmount,
                netPayable
        );
    }

    private LedgerMaster getOrCreateSystemLedger(
            LedgerType ledgerType,
            LedgerGroupType groupType,
            String ledgerName,
            String ledgerCode,
            DebitCredit normalBalance
    ) {
        Optional<LedgerMaster> existingByType =
                ledgerMasterRepository
                        .findByLedgerTypeAndDeletedFalse(
                                ledgerType
                        );

        if (existingByType.isPresent()) {
            LedgerMaster ledger =
                    existingByType.get();

            if (!ledger.isActive()) {
                ledger.setActive(true);

                return ledgerMasterRepository.save(
                        ledger
                );
            }

            return ledger;
        }

        Optional<LedgerMaster> existingByCode =
                ledgerMasterRepository
                        .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                                ledgerCode
                        );

        if (existingByCode.isPresent()) {
            return existingByCode.get();
        }

        LedgerGroup group =
                getOrCreateLedgerGroup(
                        groupType
                );

        LedgerMaster ledger =
                new LedgerMaster();

        ledger.setLedgerName(ledgerName);
        ledger.setLedgerCode(ledgerCode);
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(group);

        ledger.setOpeningBalance(zero());
        ledger.setOpeningBalanceType(
                normalBalance
        );

        ledger.setCurrentBalance(zero());
        ledger.setCurrentBalanceType(
                normalBalance
        );

        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        try {
            return ledgerMasterRepository.saveAndFlush(
                    ledger
            );

        } catch (DataIntegrityViolationException exception) {
            return ledgerMasterRepository
                    .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                            ledgerCode
                    )
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerGroup getOrCreateLedgerGroup(
            LedgerGroupType groupType
    ) {
        return ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(
                        groupType
                )
                .map(group -> {
                    if (!group.isActive()) {
                        group.setActive(true);

                        return ledgerGroupRepository.save(
                                group
                        );
                    }

                    return group;
                })
                .orElseGet(() -> {
                    LedgerGroup group =
                            LedgerGroup.builder()
                                    .name(
                                            formatGroupName(
                                                    groupType
                                            )
                                    )
                                    .groupType(groupType)
                                    .description(
                                            "System-created default ledger group"
                                    )
                                    .systemDefault(true)
                                    .active(true)
                                    .deleted(false)
                                    .build();

                    return ledgerGroupRepository.save(
                            group
                    );
                });
    }

    private AccountingVoucherEntryRequestDto debitEntry(
            LedgerMaster ledger,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(
                        ledger.getId()
                )
                .debitAmount(
                        money(amount)
                )
                .creditAmount(
                        zero()
                )
                .narration(narration)
                .build();
    }

    private AccountingVoucherEntryRequestDto creditEntry(
            LedgerMaster ledger,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(
                        ledger.getId()
                )
                .debitAmount(
                        zero()
                )
                .creditAmount(
                        money(amount)
                )
                .narration(narration)
                .build();
    }

    private VendorPaymentApprovalAccountingResult buildResult(
            CalculatedAmounts amounts,
            AccountingVoucherResponseDto voucher,
            boolean alreadyPosted,
            LedgerMaster purchaseLedger,
            LedgerMaster inputCgstLedger,
            LedgerMaster inputSgstLedger,
            LedgerMaster inputIgstLedger,
            LedgerMaster tdsPayableLedger
    ) {
        return VendorPaymentApprovalAccountingResult.builder()
                .price(
                        amounts.price()
                )
                .cgstAmount(
                        amounts.cgstAmount()
                )
                .sgstAmount(
                        amounts.sgstAmount()
                )
                .igstAmount(
                        amounts.igstAmount()
                )
                .totalGstAmount(
                        amounts.totalGstAmount()
                )
                .grossInvoiceAmount(
                        amounts.grossInvoiceAmount()
                )
                .tdsAmount(
                        amounts.tdsAmount()
                )
                .vendorNetPayableAmount(
                        amounts.vendorNetPayableAmount()
                )
                .purchaseLedgerId(
                        id(purchaseLedger)
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
                .voucher(voucher)
                .alreadyPosted(
                        alreadyPosted
                )
                .build();
    }

    private void validateRequest(
            ExternalVendor externalVendor,
            LedgerMaster vendorLedger,
            VendorPaymentApprovalRequestDto request
    ) {
        if (externalVendor == null
                || externalVendor.getId() == null) {

            throw new ValidationException(
                    "External vendor is required",
                    "ERR_EXTERNAL_VENDOR_REQUIRED",
                    "externalVendor"
            );
        }

        if (vendorLedger == null
                || vendorLedger.getId() == null) {

            throw new ValidationException(
                    "Vendor ledger is required",
                    "ERR_VENDOR_LEDGER_REQUIRED",
                    "vendorLedger"
            );
        }

        if (!vendorLedger.isActive()) {
            throw new ValidationException(
                    "Vendor ledger is inactive",
                    "ERR_VENDOR_LEDGER_INACTIVE",
                    "vendorLedger"
            );
        }

        if (request == null) {
            throw new ValidationException(
                    "Payment approval details are required",
                    "ERR_PAYMENT_APPROVAL_REQUIRED",
                    "paymentApproval"
            );
        }

        if (request.getProcurementPaymentRequestId() == null
                || request.getProcurementPaymentRequestId() <= 0) {

            throw new ValidationException(
                    "Valid procurement payment request ID is required",
                    "ERR_PAYMENT_REQUEST_ID_REQUIRED",
                    "paymentApproval.procurementPaymentRequestId"
            );
        }

        if (request.getPrice() == null
                || request.getPrice()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new ValidationException(
                    "Price must be greater than zero",
                    "ERR_INVALID_VENDOR_PRICE",
                    "paymentApproval.price"
            );
        }

        parsePaymentGstRegistrationType(
                request.getGstRegistrationType()
        );
    }

    private GstRegistrationType parsePaymentGstRegistrationType(
            String value
    ) {
        if (!hasText(value)) {
            return null;
        }

        try {
            return GstRegistrationType.valueOf(
                    value.trim()
                            .toUpperCase(Locale.ROOT)
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

    private String buildNarration(
            ExternalVendor vendor,
            VendorPaymentApprovalRequestDto request,
            CalculatedAmounts amounts
    ) {
        return "Vendor invoice approved for "
                + vendor.getVendorName()
                + ", invoice "
                + safeInvoiceReference(request)
                + ", gross "
                + amounts.grossInvoiceAmount()
                + ", TDS "
                + amounts.tdsAmount()
                + ", vendor net payable "
                + amounts.vendorNetPayableAmount();
    }

    private String safeInvoiceReference(
            VendorPaymentApprovalRequestDto request
    ) {
        if (hasText(request.getInvoiceNumber())) {
            return request.getInvoiceNumber().trim();
        }

        return "payment request "
                + request.getProcurementPaymentRequestId();
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
                .orElse(
                        groupType.name()
                );
    }

    private String normalizeEnum(
            String value
    ) {
        return hasText(value)
                ? value.trim()
                .toUpperCase(Locale.ROOT)
                : null;
    }

    private BigDecimal percentageAmount(
            BigDecimal amount,
            BigDecimal percentage
    ) {
        return amount.multiply(
                        percentage
                )
                .divide(
                        HUNDRED,
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal percentage(
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

    private BigDecimal money(
            BigDecimal value
    ) {
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
}
