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
    @Transactional
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

        CalculatedAmounts amounts = calculateAmounts(request);

        Optional<AccountingVoucher> existingVoucher =
                accountingVoucherRepository
                        .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                                VoucherSourceType.PROCUREMENT_VENDOR_INVOICE,
                                request.getProcurementPaymentRequestId(),
                                VoucherStatus.POSTED
                        );

        if (existingVoucher.isPresent()) {
            AccountingVoucherResponseDto voucherResponse =
                    accountingVoucherService.getVoucherById(
                            existingVoucher.get().getId()
                    );

            return PaymentAccountingResult.builder()
                    .amounts(amounts)
                    .voucher(voucherResponse)
                    .alreadyPosted(true)
                    .build();
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
                                + resolveInvoiceReference(request)
                )
        );

        if (amounts.cgstAmount().compareTo(BigDecimal.ZERO) > 0) {
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
                            "Input CGST for "
                                    + resolveInvoiceReference(request)
                    )
            );
        }

        if (amounts.sgstAmount().compareTo(BigDecimal.ZERO) > 0) {
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
                            "Input SGST for "
                                    + resolveInvoiceReference(request)
                    )
            );
        }

        if (amounts.igstAmount().compareTo(BigDecimal.ZERO) > 0) {
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
                            "Input IGST for "
                                    + resolveInvoiceReference(request)
                    )
            );
        }

        entries.add(
                creditEntry(
                        vendorLedger,
                        amounts.vendorNetPayableAmount(),
                        "Net amount payable to vendor "
                                + externalVendor.getVendorName()
                )
        );

        if (amounts.tdsAmount().compareTo(BigDecimal.ZERO) > 0) {
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
                            "TDS deducted for "
                                    + resolveInvoiceReference(request)
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
                        .voucherType(VoucherType.PURCHASE_INVOICE)
                        .voucherDate(voucherDate)
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
                        .entries(entries)
                        .build();

        AccountingVoucherResponseDto voucherResponse =
                accountingVoucherService.createVoucher(voucherRequest);

        return PaymentAccountingResult.builder()
                .amounts(amounts)
                .voucher(voucherResponse)
                .alreadyPosted(false)
                .purchaseLedgerId(purchaseLedger.getId())
                .inputCgstLedgerId(id(inputCgstLedger))
                .inputSgstLedgerId(id(inputSgstLedger))
                .inputIgstLedgerId(id(inputIgstLedger))
                .tdsPayableLedgerId(id(tdsPayableLedger))
                .build();
    }

    private CalculatedAmounts calculateAmounts(
            VendorPaymentApprovalRequestDto request
    ) {
        BigDecimal price = money(request.getPrice());

        GstRegistrationType registrationType =
                parsePaymentGstRegistrationType(
                        request.getGstRegistrationType()
                );

        String supplyType =
                normalizeEnum(request.getGstSupplyType());

        BigDecimal gstPercentage =
                rate(request.getGstPercentage());

        BigDecimal cgstAmount = ZERO;
        BigDecimal sgstAmount = ZERO;
        BigDecimal igstAmount = ZERO;
        BigDecimal totalGstAmount = ZERO;

        if (registrationType == null) {
            if (gstPercentage.compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(
                        "GST registration type is required when GST percentage is provided",
                        "ERR_GST_REGISTRATION_TYPE_REQUIRED",
                        "paymentApproval.gstRegistrationType"
                );
            }
        } else if (registrationType.isZeroRated()) {
            if (gstPercentage.compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(
                        "GST percentage must be zero for "
                                + registrationType,
                        "ERR_ZERO_RATED_GST_PERCENTAGE_NOT_ALLOWED",
                        "paymentApproval.gstPercentage"
                );
            }

            totalGstAmount = ZERO;

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
                        "GST supply type must be INTRA_STATE or INTER_STATE for "
                                + registrationType,
                        "ERR_INVALID_GST_SUPPLY_TYPE",
                        "paymentApproval.gstSupplyType"
                );
            }
        }

        BigDecimal grossInvoiceAmount =
                price.add(totalGstAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        boolean tdsActive =
                Boolean.TRUE.equals(request.getTdsActive());

        BigDecimal tdsPercentage =
                rate(request.getTdsPercentage());

        BigDecimal tdsAmount = ZERO;

        if (tdsActive) {
            if (tdsPercentage.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "TDS percentage must be greater than zero when TDS is active",
                        "ERR_TDS_PERCENTAGE_REQUIRED",
                        "paymentApproval.tdsPercentage"
                );
            }

            tdsAmount =
                    percentageAmount(
                            price,
                            tdsPercentage
                    );

        } else if (tdsPercentage.compareTo(BigDecimal.ZERO) != 0) {
            throw new ValidationException(
                    "TDS percentage must be zero when TDS is inactive",
                    "ERR_TDS_PERCENTAGE_NOT_ALLOWED",
                    "paymentApproval.tdsPercentage"
            );
        }

        BigDecimal vendorNetPayableAmount =
                grossInvoiceAmount.subtract(tdsAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

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

        AccountingVoucherResponseDto voucher =
                paymentResult != null
                        ? paymentResult.voucher()
                        : null;

        CalculatedAmounts amounts =
                paymentResult != null
                        ? paymentResult.amounts()
                        : null;

        boolean voucherCreated = voucher != null;

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
                .vendorName(
                        externalVendor.getVendorName()
                )

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

                .price(
                        amounts != null
                                ? amounts.price()
                                : null
                )
                .cgstAmount(
                        amounts != null
                                ? amounts.cgstAmount()
                                : null
                )
                .sgstAmount(
                        amounts != null
                                ? amounts.sgstAmount()
                                : null
                )
                .igstAmount(
                        amounts != null
                                ? amounts.igstAmount()
                                : null
                )
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

                .voucherCreated(voucherCreated)
                .voucherId(
                        voucherCreated
                                ? voucher.getId()
                                : null
                )
                .voucherNumber(
                        voucherCreated
                                ? voucher.getVoucherNumber()
                                : null
                )
                .voucherType(
                        voucherCreated
                                && voucher.getVoucherType() != null
                                ? voucher.getVoucherType().name()
                                : null
                )
                .voucherSourceType(
                        voucherCreated
                                && voucher.getSourceType() != null
                                ? voucher.getSourceType().name()
                                : null
                )
                .voucherSourceId(
                        voucherCreated
                                ? voucher.getSourceId()
                                : null
                )
                .voucherDate(
                        voucherCreated
                                ? voucher.getVoucherDate()
                                : null
                )
                .totalDebit(
                        voucherCreated
                                ? voucher.getTotalDebit()
                                : ZERO
                )
                .totalCredit(
                        voucherCreated
                                ? voucher.getTotalCredit()
                                : ZERO
                )
                .voucherStatus(
                        voucherCreated
                                && voucher.getStatus() != null
                                ? voucher.getStatus().name()
                                : null
                )

                .syncStatus("SUCCESS")
                .syncedAt(
                        externalVendor.getLastSyncedAt()
                )
                .message(
                        paymentResult == null
                                ? "Vendor and vendor ledger synchronized successfully"
                                : paymentResult.alreadyPosted()
                                ? "Vendor synchronized; accounting voucher was already posted"
                                : "Vendor, vendor ledger and accounting voucher created successfully"
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

        parsePaymentGstRegistrationType(
                request.getGstRegistrationType()
        );
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
        return "Vendor invoice approved for "
                + vendor.getVendorName()
                + ", invoice "
                + resolveInvoiceReference(request)
                + ", gross invoice amount "
                + amounts.grossInvoiceAmount()
                + ", TDS "
                + amounts.tdsAmount()
                + ", vendor net payable "
                + amounts.vendorNetPayableAmount();
    }

    private String resolveInvoiceReference(
            VendorPaymentApprovalRequestDto request
    ) {
        if (hasText(request.getInvoiceNumber())) {
            return request.getInvoiceNumber().trim();
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
            AccountingVoucherResponseDto voucher,
            boolean alreadyPosted,
            Long purchaseLedgerId,
            Long inputCgstLedgerId,
            Long inputSgstLedgerId,
            Long inputIgstLedgerId,
            Long tdsPayableLedgerId
    ) {
    }
}
