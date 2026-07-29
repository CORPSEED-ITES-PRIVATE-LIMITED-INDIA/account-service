package com.account.serviceImpl.vendor;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.domain.vendor.ExternalVendor;
import com.account.dto.vendor.AccountVendorSyncRequestDto;
import com.account.dto.vendor.AccountVendorSyncResponseDto;
import com.account.exception.ValidationException;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.repository.vendor.ExternalVendorRepository;
import com.account.service.vendor.ExternalVendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public AccountVendorSyncResponseDto syncVendor(
            AccountVendorSyncRequestDto request
    ) {
        validateRequest(request);

        log.info(
                "Starting external vendor synchronization. operationVendorId={}, vendorName={}",
                request.getOperationVendorId(),
                request.getVendorName()
        );

        Optional<ExternalVendor> existingVendorOptional =
                externalVendorRepository
                        .findByOperationVendorIdAndDeletedFalse(
                                request.getOperationVendorId()
                        );

        boolean newVendor = existingVendorOptional.isEmpty();

        ExternalVendor externalVendor =
                existingVendorOptional
                        .orElseGet(ExternalVendor::new);

        LedgerGroup sundryCreditorsGroup =
                getOrCreateSundryCreditorsGroup();

        LedgerMaster ledger =
                externalVendor.getLedger();

        /*
         * Create ledger only when:
         * 1. External vendor is new
         * 2. Existing external vendor does not have a ledger
         */
        if (ledger == null) {

            ledger = createVendorLedger(
                    request,
                    sundryCreditorsGroup
            );

        } else {

            updateVendorLedger(
                    ledger,
                    request,
                    sundryCreditorsGroup
            );
        }

        LedgerMaster savedLedger =
                ledgerMasterRepository.save(ledger);

        updateExternalVendor(
                externalVendor,
                request,
                savedLedger
        );

        ExternalVendor savedExternalVendor =
                externalVendorRepository.save(
                        externalVendor
                );

        String action =
                newVendor ? "CREATED" : "UPDATED";

        log.info(
                "External vendor synchronized successfully. operationVendorId={}, externalVendorId={}, ledgerId={}, action={}",
                request.getOperationVendorId(),
                savedExternalVendor.getId(),
                savedLedger.getId(),
                action
        );

        return buildResponse(
                savedExternalVendor,
                savedLedger,
                action
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

        ledger.setLedgerType(
                LedgerType.VENDOR
        );

        ledger.setLedgerGroup(
                sundryCreditorsGroup
        );

        /*
         * Vendor is a creditor.
         * Therefore its natural balance is CREDIT.
         */
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
                Boolean.TRUE.equals(request.getActive())
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

        ledger.setSystemCreated(true);
        ledger.setActive(
                Boolean.TRUE.equals(request.getActive())
        );
        ledger.setDeleted(false);
    }

    private void applyLedgerDetails(
            LedgerMaster ledger,
            AccountVendorSyncRequestDto request
    ) {
        ledger.setGstNo(
                clean(request.getGstNumber())
        );

        ledger.setPanNo(
                clean(request.getPan())
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
                clean(request.getIfscCode())
        );

        ledger.setBranchName(
                clean(request.getBranchAddress())
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
                clean(request.getEmail())
        );

        externalVendor.setMobile(
                clean(request.getMobile())
        );

        externalVendor.setPanNumber(
                clean(request.getPan())
        );

        externalVendor.setGstNumber(
                clean(request.getGstNumber())
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
                clean(request.getIfscCode())
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
                                            LedgerGroupType.SUNDRY_CREDITORS
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
        String baseCode = String.format(
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
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            return GstRegistrationType.valueOf(
                    value.trim()
                            .toUpperCase(Locale.ROOT)
            );

        } catch (IllegalArgumentException exception) {

            throw new ValidationException(
                    "Invalid GST registration type: " + value,
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

        if (request.getVendorName() == null
                || request.getVendorName().trim().isEmpty()) {

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
    }

    private AccountVendorSyncResponseDto buildResponse(
            ExternalVendor externalVendor,
            LedgerMaster ledger,
            String action
    ) {
        LedgerGroup ledgerGroup =
                ledger.getLedgerGroup();

        return AccountVendorSyncResponseDto.builder()
                .externalVendorId(
                        externalVendor.getId()
                )
                .operationVendorId(
                        externalVendor.getOperationVendorId()
                )
                .vendorAccountsSubmissionId(
                        externalVendor.getVendorAccountsSubmissionId()
                )
                .vendorFinalizationId(
                        externalVendor.getVendorFinalizationId()
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
                .syncStatus("SUCCESS")
                .syncedAt(
                        externalVendor.getLastSyncedAt()
                )
                .message(
                        "CREATED".equals(action)
                                ? "External vendor and vendor ledger created successfully"
                                : "External vendor and vendor ledger updated successfully"
                )
                .build();
    }

    private String normalizeName(String value) {
        return value == null
                ? null
                : value.trim()
                .replaceAll("\\s+", " ");
    }

    private String clean(String value) {
        return value == null
                || value.trim().isEmpty()
                ? null
                : value.trim();
    }
}