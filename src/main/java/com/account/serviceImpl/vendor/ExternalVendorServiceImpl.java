package com.account.serviceImpl.vendor;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.ledger.*;
import com.account.domain.vendor.ExternalVendor;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.vendor.*;
import com.account.enm.VendorVoucherLedgerSource;
import com.account.exception.ValidationException;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.repository.vendor.ExternalVendorRepository;
import com.account.service.ledger.AccountingVoucherService;
import com.account.service.vendor.ExternalVendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalVendorServiceImpl
        implements ExternalVendorService {

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(
                    2,
                    RoundingMode.HALF_UP
            );

    private final ExternalVendorRepository externalVendorRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final AccountingVoucherService accountingVoucherService;

    @Override
    @Transactional
    public AccountVendorSyncResponseDto syncVendor(
            AccountVendorSyncRequestDto request
    ) {
        validateRequest(request);

        log.info(
                "Starting vendor synchronization. operationVendorId={}, "
                        + "vendorName={}, voucherRequested={}",
                request.getOperationVendorId(),
                request.getVendorName(),
                request.getVoucherDetails() != null
        );

        Optional<ExternalVendor> existingVendorOptional =
                externalVendorRepository
                        .findByOperationVendorIdAndDeletedFalse(
                                request.getOperationVendorId()
                        );

        boolean newVendor =
                existingVendorOptional.isEmpty();

        ExternalVendor externalVendor =
                existingVendorOptional
                        .orElseGet(ExternalVendor::new);

        LedgerGroup sundryCreditorsGroup =
                getOrCreateSundryCreditorsGroup();

        LedgerMaster vendorLedger =
                externalVendor.getLedger();

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

        /*
         * Ledger must be saved first because the voucher entry
         * requires the generated vendor ledger ID.
         */
        LedgerMaster savedVendorLedger =
                ledgerMasterRepository.saveAndFlush(
                        vendorLedger
                );

        updateExternalVendor(
                externalVendor,
                request,
                savedVendorLedger
        );

        ExternalVendor savedExternalVendor =
                externalVendorRepository.saveAndFlush(
                        externalVendor
                );

        AccountingVoucherResponseDto voucherResponse =
                null;

        if (request.getVoucherDetails() != null) {
            voucherResponse = createVendorVoucher(
                    savedExternalVendor,
                    savedVendorLedger,
                    request.getVoucherDetails()
            );
        }

        String action =
                newVendor ? "CREATED" : "UPDATED";

        log.info(
                "Vendor synchronization completed. "
                        + "operationVendorId={}, externalVendorId={}, "
                        + "ledgerId={}, action={}, voucherId={}",
                request.getOperationVendorId(),
                savedExternalVendor.getId(),
                savedVendorLedger.getId(),
                action,
                voucherResponse != null
                        ? voucherResponse.getId()
                        : null
        );

        return buildResponse(
                savedExternalVendor,
                savedVendorLedger,
                voucherResponse,
                action
        );
    }

    private AccountingVoucherResponseDto createVendorVoucher(
            ExternalVendor externalVendor,
            LedgerMaster vendorLedger,
            VendorVoucherRequestDto voucherDetails
    ) {
        validateVendorVoucherRequest(
                voucherDetails
        );

        List<AccountingVoucherEntryRequestDto> resolvedEntries =
                new ArrayList<>();

        int vendorLedgerEntryCount = 0;

        for (
                int index = 0;
                index < voucherDetails.getEntries().size();
                index++
        ) {
            VendorVoucherEntryRequestDto incomingEntry =
                    voucherDetails.getEntries().get(index);

            if (incomingEntry == null) {
                throw new ValidationException(
                        "Voucher entry is required at index " + index,
                        "ERR_VENDOR_VOUCHER_ENTRY_REQUIRED",
                        "voucherDetails.entries[" + index + "]"
                );
            }

            if (incomingEntry.getLedgerSource() == null) {
                throw new ValidationException(
                        "Ledger source is required at entry index "
                                + index,
                        "ERR_LEDGER_SOURCE_REQUIRED",
                        "voucherDetails.entries["
                                + index
                                + "].ledgerSource"
                );
            }

            BigDecimal debitAmount =
                    money(incomingEntry.getDebitAmount());

            BigDecimal creditAmount =
                    money(incomingEntry.getCreditAmount());

            Long resolvedLedgerId;

            if (
                    incomingEntry.getLedgerSource()
                            == VendorVoucherLedgerSource
                            .VENDOR_LEDGER
            ) {
                resolvedLedgerId =
                        vendorLedger.getId();

                vendorLedgerEntryCount++;

                validateVendorLedgerDirection(
                        voucherDetails.getVoucherType(),
                        debitAmount,
                        creditAmount,
                        index
                );

            } else {
                if (
                        incomingEntry.getLedgerId() == null
                                || incomingEntry.getLedgerId() <= 0
                ) {
                    throw new ValidationException(
                            "Ledger ID is required for EXISTING_LEDGER "
                                    + "at entry index "
                                    + index,
                            "ERR_EXISTING_LEDGER_ID_REQUIRED",
                            "voucherDetails.entries["
                                    + index
                                    + "].ledgerId"
                    );
                }

                resolvedLedgerId =
                        incomingEntry.getLedgerId();
            }

            resolvedEntries.add(
                    AccountingVoucherEntryRequestDto.builder()
                            .ledgerId(resolvedLedgerId)
                            .debitAmount(debitAmount)
                            .creditAmount(creditAmount)
                            .narration(
                                    clean(
                                            incomingEntry
                                                    .getNarration()
                                    )
                            )
                            .build()
            );
        }

        if (vendorLedgerEntryCount == 0) {
            throw new ValidationException(
                    "One VENDOR_LEDGER entry is required",
                    "ERR_VENDOR_LEDGER_ENTRY_REQUIRED",
                    "voucherDetails.entries"
            );
        }

        if (vendorLedgerEntryCount > 1) {
            throw new ValidationException(
                    "Only one VENDOR_LEDGER entry is allowed",
                    "ERR_MULTIPLE_VENDOR_LEDGER_ENTRIES",
                    "voucherDetails.entries"
            );
        }

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(
                                voucherDetails.getVoucherType()
                        )
                        .voucherDate(
                                voucherDetails.getVoucherDate()
                        )
                        .sourceType(
                                voucherDetails.getSourceType()
                        )
                        .sourceId(
                                voucherDetails.getSourceId()
                        )
                        .narration(
                                resolveVoucherNarration(
                                        externalVendor,
                                        voucherDetails
                                )
                        )
                        .entries(resolvedEntries)
                        .build();

        /*
         * Existing AccountingVoucherService performs:
         *
         * - ledger validation
         * - active-ledger validation
         * - debit/credit validation
         * - balanced-total validation
         * - duplicate source validation
         * - voucher save
         * - voucher-entry save
         * - ledger balance update
         */
        return accountingVoucherService.createVoucher(
                voucherRequest
        );
    }

    private void validateVendorLedgerDirection(
            VoucherType voucherType,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            int index
    ) {
        if (voucherType == VoucherType.PAYMENT) {
            /*
             * Payment to vendor:
             *
             * Vendor Ledger Dr
             *      To Bank
             *      To TDS Payable
             */
            if (debitAmount.compareTo(BigDecimal.ZERO) <= 0
                    || creditAmount.compareTo(BigDecimal.ZERO) != 0) {

                throw new ValidationException(
                        "For PAYMENT voucher, vendor ledger "
                                + "must contain a debit amount",
                        "ERR_VENDOR_PAYMENT_DIRECTION_INVALID",
                        "voucherDetails.entries["
                                + index
                                + "]"
                );
            }
        }

        if (voucherType == VoucherType.PURCHASE_INVOICE) {
            /*
             * Vendor invoice booking:
             *
             * Purchase/Expense Dr
             * Input GST Dr
             *      To Vendor Ledger
             */
            if (creditAmount.compareTo(BigDecimal.ZERO) <= 0
                    || debitAmount.compareTo(BigDecimal.ZERO) != 0) {

                throw new ValidationException(
                        "For PURCHASE_INVOICE voucher, vendor ledger "
                                + "must contain a credit amount",
                        "ERR_VENDOR_INVOICE_DIRECTION_INVALID",
                        "voucherDetails.entries["
                                + index
                                + "]"
                );
            }
        }
    }

    private void validateVendorVoucherRequest(
            VendorVoucherRequestDto voucherDetails
    ) {
        if (voucherDetails == null) {
            return;
        }

        if (voucherDetails.getVoucherType() == null) {
            throw new ValidationException(
                    "Voucher type is required",
                    "ERR_VOUCHER_TYPE_REQUIRED",
                    "voucherDetails.voucherType"
            );
        }

        if (voucherDetails.getSourceType() == null) {
            throw new ValidationException(
                    "Voucher source type is required",
                    "ERR_VOUCHER_SOURCE_TYPE_REQUIRED",
                    "voucherDetails.sourceType"
            );
        }

        if (voucherDetails.getSourceId() == null
                || voucherDetails.getSourceId() <= 0) {

            throw new ValidationException(
                    "Valid voucher source ID is required",
                    "ERR_VOUCHER_SOURCE_ID_REQUIRED",
                    "voucherDetails.sourceId"
            );
        }

        if (voucherDetails.getVoucherDate() == null) {
            throw new ValidationException(
                    "Voucher date is required",
                    "ERR_VOUCHER_DATE_REQUIRED",
                    "voucherDetails.voucherDate"
            );
        }

        if (voucherDetails.getEntries() == null
                || voucherDetails.getEntries().size() < 2) {

            throw new ValidationException(
                    "At least two voucher entries are required",
                    "ERR_MIN_TWO_VOUCHER_ENTRIES_REQUIRED",
                    "voucherDetails.entries"
            );
        }
    }

    private LedgerMaster createVendorLedger(
            AccountVendorSyncRequestDto request,
            LedgerGroup sundryCreditorsGroup
    ) {
        LedgerMaster ledger =
                new LedgerMaster();

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

        ledger.setLedgerType(
                LedgerType.VENDOR
        );

        ledger.setLedgerGroup(
                sundryCreditorsGroup
        );

        ledger.setOpeningBalance(ZERO);
        ledger.setOpeningBalanceType(
                DebitCredit.CREDIT
        );

        ledger.setCurrentBalance(ZERO);
        ledger.setCurrentBalanceType(
                DebitCredit.CREDIT
        );

        applyLedgerDetails(
                ledger,
                request
        );

        ledger.setSystemCreated(true);

        ledger.setActive(
                Boolean.TRUE.equals(
                        request.getActive()
                )
        );

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

        ledger.setLedgerType(
                LedgerType.VENDOR
        );

        ledger.setLedgerGroup(
                sundryCreditorsGroup
        );

        applyLedgerDetails(
                ledger,
                request
        );

        /*
         * Do not reset current balance here because existing
         * vendor transactions may already exist.
         */
        ledger.setSystemCreated(true);

        ledger.setActive(
                Boolean.TRUE.equals(
                        request.getActive()
                )
        );

        ledger.setDeleted(false);
    }

    private void applyLedgerDetails(
            LedgerMaster ledger,
            AccountVendorSyncRequestDto request
    ) {
        ledger.setGstNo(
                cleanUpperCase(
                        request.getGstNumber()
                )
        );

        ledger.setPanNo(
                cleanUpperCase(
                        request.getPan()
                )
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
                cleanUpperCase(
                        request.getIfscCode()
                )
        );

        /*
         * LedgerMaster.branchName has maximum length 100.
         */
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
                normalizeName(
                        request.getVendorName()
                )
        );

        externalVendor.setEmail(
                cleanLowerCase(
                        request.getEmail()
                )
        );

        externalVendor.setMobile(
                clean(request.getMobile())
        );

        externalVendor.setPanNumber(
                cleanUpperCase(
                        request.getPan()
                )
        );

        externalVendor.setGstNumber(
                cleanUpperCase(
                        request.getGstNumber()
                )
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
                cleanUpperCase(
                        request.getIfscCode()
                )
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
                Boolean.TRUE.equals(
                        request.getActive()
                )
        );

        externalVendor.setDeleted(false);
    }

    private LedgerGroup getOrCreateSundryCreditorsGroup() {
        return ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(
                        LedgerGroupType.SUNDRY_CREDITORS
                )
                .map(existingGroup -> {
                    if (!existingGroup.isActive()) {
                        existingGroup.setActive(true);

                        return ledgerGroupRepository.save(
                                existingGroup
                        );
                    }

                    return existingGroup;
                })
                .orElseGet(() -> {
                    LedgerGroup ledgerGroup =
                            LedgerGroup.builder()
                                    .name("Sundry Creditors")
                                    .groupType(
                                            LedgerGroupType
                                                    .SUNDRY_CREDITORS
                                    )
                                    .description(
                                            "System-created ledger group for external vendors"
                                    )
                                    .systemDefault(true)
                                    .active(true)
                                    .deleted(false)
                                    .build();

                    return ledgerGroupRepository.save(
                            ledgerGroup
                    );
                });
    }

    private String resolveLedgerName(
            String vendorName,
            Long operationVendorId,
            Long existingLedgerId
    ) {
        String normalizedName =
                normalizeName(vendorName);

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
                    value.trim()
                            .toUpperCase(Locale.ROOT)
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
                && request.getGstNumber()
                .trim()
                .length() != 15) {

            throw new ValidationException(
                    "GST number must contain exactly 15 characters",
                    "ERR_INVALID_VENDOR_GST_LENGTH",
                    "gstNumber"
            );
        }

        parseGstRegistrationType(
                request.getGstRegistrationType()
        );

        if (request.getVoucherDetails() != null) {
            validateVendorVoucherRequest(
                    request.getVoucherDetails()
            );
        }
    }

    private AccountVendorSyncResponseDto buildResponse(
            ExternalVendor externalVendor,
            LedgerMaster ledger,
            AccountingVoucherResponseDto voucher,
            String action
    ) {
        LedgerGroup ledgerGroup =
                ledger.getLedgerGroup();

        boolean voucherCreated =
                voucher != null;

        return AccountVendorSyncResponseDto.builder()
                .externalVendorId(
                        externalVendor.getId()
                )
                .operationVendorId(
                        externalVendor.getOperationVendorId()
                )
                .vendorAccountsSubmissionId(
                        externalVendor
                                .getVendorAccountsSubmissionId()
                )
                .vendorFinalizationId(
                        externalVendor.getVendorFinalizationId()
                )
                .vendorName(
                        externalVendor.getVendorName()
                )

                .ledgerId(
                        ledger.getId()
                )
                .ledgerCode(
                        ledger.getLedgerCode()
                )
                .ledgerName(
                        ledger.getLedgerName()
                )
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
                                ? ledgerGroup
                                .getGroupType()
                                .name()
                                : null
                )

                .action(action)
                .active(
                        externalVendor.isActive()
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
                        voucherCreated
                                ? "Vendor, vendor ledger and accounting voucher created successfully"
                                : "Vendor and vendor ledger synchronized successfully"
                )
                .build();
    }

    private String resolveVoucherNarration(
            ExternalVendor externalVendor,
            VendorVoucherRequestDto voucherDetails
    ) {
        if (hasText(voucherDetails.getNarration())) {
            return voucherDetails.getNarration().trim();
        }

        return "Accounting voucher posted for vendor: "
                + externalVendor.getVendorName();
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
        String cleaned =
                clean(value);

        return cleaned == null
                ? null
                : cleaned.toUpperCase(Locale.ROOT);
    }

    private String cleanLowerCase(
            String value
    ) {
        String cleaned =
                clean(value);

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
                : value.substring(0, maximumLength);
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.trim().isEmpty();
    }
}