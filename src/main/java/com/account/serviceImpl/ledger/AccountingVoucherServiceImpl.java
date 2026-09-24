package com.account.serviceImpl.ledger;

import com.account.domain.ledger.*;
import com.account.dto.ledger.*;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.ledger.AccountingVoucherRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.ledger.AccountingVoucherService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountingVoucherServiceImpl implements AccountingVoucherService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final AccountingVoucherRepository accountingVoucherRepository;
    private final LedgerMasterRepository ledgerMasterRepository;

    @Override
    @Transactional
    public AccountingVoucherResponseDto createVoucher(AccountingVoucherRequestDto request) {

        log.info("Creating accounting voucher. voucherType={}, sourceType={}, sourceId={}, entryCount={}",
                request != null ? request.getVoucherType() : null,
                request != null ? request.getSourceType() : null,
                request != null ? request.getSourceId() : null,
                request != null && request.getEntries() != null ? request.getEntries().size() : 0
        );

        validateVoucherRequest(request);
        log.debug("Accounting voucher request validation completed. voucherType={}", request.getVoucherType());

        VoucherSourceType sourceType = request.getSourceType() == null
                ? VoucherSourceType.MANUAL
                : request.getSourceType();

        validateDuplicateSource(sourceType, request.getSourceId());
        log.debug("Duplicate source validation completed. sourceType={}, sourceId={}", sourceType, request.getSourceId());

        Map<Long, LedgerMaster> lockedLedgers = lockRequestedLedgers(request);

        /*
         * Re-check after ledger locks are acquired. For normal duplicate retries
         * using the same ledgers, this closes the common check-then-insert race.
         * A database-level idempotency key is still recommended for absolute
         * cross-node protection.
         */
        validateDuplicateSource(sourceType, request.getSourceId());


        LedgerMaster partyLedger = resolveOptionalPartyLedger(
                request.getPartyLedgerId(),
                lockedLedgers
        );
        AccountingVoucher voucher = AccountingVoucher.builder()
                .voucherNumber(generateVoucherNumber(request.getVoucherType()))
                .voucherType(request.getVoucherType())
                .voucherDate(
                        request.getVoucherDate() != null
                                ? request.getVoucherDate()
                                : LocalDate.now()
                )
                .sourceType(sourceType)
                .sourceId(request.getSourceId())
                .status(VoucherStatus.POSTED)
                .narration(clean(request.getNarration()))
                .projectId(request.getProjectId())
                .projectNo(clean(request.getProjectNo()))
                .projectName(clean(request.getProjectName()))
                .clientCompanyId(request.getClientCompanyId())
                .clientCompanyName(clean(request.getClientCompanyName()))
                .clientUnitId(request.getClientUnitId())
                .clientUnitName(clean(request.getClientUnitName()))
                .expensePaidBy(clean(request.getExpensePaidBy()))
                .partyLedger(partyLedger)
                .build();



        int order = 1;

        for (AccountingVoucherEntryRequestDto entryRequest : request.getEntries()) {

            LedgerMaster ledger = lockedLedgers.get(entryRequest.getLedgerId());

            if (ledger == null) {
                log.warn(
                        "Ledger not found while creating voucher. ledgerId={}",
                        entryRequest.getLedgerId()
                );
                throw new ResourceNotFoundException(
                        "Ledger not found with ID: " + entryRequest.getLedgerId(),
                        "LEDGER_NOT_FOUND"
                );
            }

            if (!ledger.isActive()) {
                log.warn("Inactive ledger used in voucher request. ledgerId={}, ledgerName={}", ledger.getId(), ledger.getLedgerName());
                throw new ValidationException(
                        "Ledger is inactive: " + ledger.getLedgerName(),
                        "ERR_LEDGER_INACTIVE",
                        "ledgerId"
                );
            }



            BigDecimal debit = safeMoney(entryRequest.getDebitAmount());
            BigDecimal credit = safeMoney(entryRequest.getCreditAmount());

            AccountingVoucherEntry entry = AccountingVoucherEntry.builder()
                    .ledger(ledger)
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .narration(clean(entryRequest.getNarration()))
                    .displayOrder(order++)
                    .build();

            voucher.addEntry(entry);

            log.info(
                    "[VOUCHER-ENTRY-RESOLVED] voucherNumber={} | sourceType={} | sourceId={} | "
                            + "displayOrder={} | ledgerId={} | ledgerCode={} | ledgerName={} | "
                            + "ledgerType={} | debit={} | credit={}",
                    voucher.getVoucherNumber(),
                    sourceType,
                    request.getSourceId(),
                    entry.getDisplayOrder(),
                    ledger.getId(),
                    ledger.getLedgerCode(),
                    ledger.getLedgerName(),
                    ledger.getLedgerType(),
                    debit,
                    credit
            );
        }

        voucher.calculateTotals();

        BigDecimal calculatedDebit = safeMoney(voucher.getTotalDebit());
        BigDecimal calculatedCredit = safeMoney(voucher.getTotalCredit());
        BigDecimal voucherDifference = calculatedDebit
                .subtract(calculatedCredit)
                .setScale(MONEY_SCALE, ROUNDING_MODE);

        log.info(
                "[VOUCHER-BALANCE-CHECK] voucherNumber={} | voucherType={} | sourceType={} | "
                        + "sourceId={} | totalDebit={} | totalCredit={} | difference={} | balanced={}",
                voucher.getVoucherNumber(),
                voucher.getVoucherType(),
                sourceType,
                request.getSourceId(),
                calculatedDebit,
                calculatedCredit,
                voucherDifference,
                voucherDifference.compareTo(BigDecimal.ZERO) == 0
        );

        AccountingVoucher saved = accountingVoucherRepository.save(voucher);
        log.info("Accounting voucher saved. voucherId={}, voucherNumber={}, voucherType={}",
                saved.getId(),
                saved.getVoucherNumber(),
                saved.getVoucherType()
        );

        // Update ledger balances only after voucher is posted
        updateLedgerBalancesForPostedVoucher(saved);
        log.info("Ledger balances updated for posted voucher. voucherId={}, voucherNumber={}", saved.getId(), saved.getVoucherNumber());

        return mapToResponse(saved);
    }

    private LedgerMaster resolveOptionalPartyLedger(
            Long partyLedgerId,
            Map<Long, LedgerMaster> lockedLedgers
    ) {
        if (partyLedgerId == null) {
            return null;
        }

        LedgerMaster ledger = lockedLedgers.get(partyLedgerId);

        if (ledger == null) {
            throw new ResourceNotFoundException(
                    "Party ledger not found in voucher entries with ID: "
                            + partyLedgerId,
                    "PARTY_LEDGER_NOT_FOUND"
            );
        }

        if (!ledger.isActive()) {
            throw new ValidationException(
                    "Party ledger is inactive: " + ledger.getLedgerName(),
                    "ERR_PARTY_LEDGER_INACTIVE",
                    "partyLedgerId"
            );
        }

        if (ledger.getLedgerType() != LedgerType.CUSTOMER) {
            throw new ValidationException(
                    "Debit-note party ledger must be a CUSTOMER ledger",
                    "ERR_INVALID_PARTY_LEDGER_TYPE",
                    "partyLedgerId"
            );
        }

        return ledger;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountingVoucherResponseDto getVoucherById(Long id) {

        log.debug("Fetching accounting voucher by id. voucherId={}", id);

        AccountingVoucher voucher = accountingVoucherRepository.findByIdAndStatusNot(id, VoucherStatus.CANCELLED)
                .orElseThrow(() -> {
                    log.warn("Accounting voucher not found or already cancelled. voucherId={}", id);
                    return new ResourceNotFoundException(
                            "Accounting voucher not found with ID: " + id,
                            "ACCOUNTING_VOUCHER_NOT_FOUND"
                    );
                });

        log.debug("Accounting voucher fetched. voucherId={}, voucherNumber={}, status={}",
                voucher.getId(),
                voucher.getVoucherNumber(),
                voucher.getStatus()
        );

        return mapToResponse(voucher);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountingVoucherResponseDto> getVouchers(
            VoucherType voucherType,
            VoucherSourceType sourceType,
            VoucherStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    ) {

        log.debug("Fetching accounting vouchers. voucherType={}, sourceType={}, status={}, fromDate={}, toDate={}, page={}, size={}",
                voucherType,
                sourceType,
                status,
                fromDate,
                toDate,
                page,
                size
        );

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 || size > 200 ? 20 : size;

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "voucherDate")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        Specification<AccountingVoucher> specification = buildSpecification(
                voucherType,
                sourceType,
                status,
                fromDate,
                toDate
        );

        Page<AccountingVoucherResponseDto> vouchers = accountingVoucherRepository.findAll(specification, pageable)
                .map(this::mapToResponse);

        log.debug("Accounting vouchers fetched. totalElements={}, totalPages={}, page={}, size={}",
                vouchers.getTotalElements(),
                vouchers.getTotalPages(),
                safePage,
                safeSize
        );

        return vouchers;
    }

    @Override
    @Transactional
    public void cancelVoucher(Long id, String reason) {

        log.info("Cancelling accounting voucher. voucherId={}", id);

        AccountingVoucher voucher = accountingVoucherRepository.findByIdAndStatusNot(id, VoucherStatus.CANCELLED)
                .orElseThrow(() -> {
                    log.warn("Accounting voucher not found or already cancelled during cancel request. voucherId={}", id);
                    return new ResourceNotFoundException(
                            "Accounting voucher not found with ID: " + id,
                            "ACCOUNTING_VOUCHER_NOT_FOUND"
                    );
                });

        if (voucher.getStatus() != VoucherStatus.POSTED) {
            log.warn("Voucher cancellation rejected because voucher is not POSTED. voucherId={}, status={}", voucher.getId(), voucher.getStatus());
            throw new ValidationException(
                    "Only POSTED voucher can be cancelled",
                    "ERR_ONLY_POSTED_VOUCHER_CAN_BE_CANCELLED",
                    "status"
            );
        }

        // Reverse ledger balances before cancellation
        reverseLedgerBalances(voucher);
        log.debug("Ledger balances reversed for voucher cancellation. voucherId={}, voucherNumber={}",
                voucher.getId(),
                voucher.getVoucherNumber()
        );

        voucher.setStatus(VoucherStatus.CANCELLED);

        String finalNarration = voucher.getNarration() == null ? "" : voucher.getNarration();
        voucher.setNarration(finalNarration + "\nCancelled Reason: " + clean(reason));

        accountingVoucherRepository.save(voucher);
        log.info("Accounting voucher cancelled successfully. voucherId={}, voucherNumber={}",
                voucher.getId(),
                voucher.getVoucherNumber()
        );
    }

    private Specification<AccountingVoucher> buildSpecification(
            VoucherType voucherType,
            VoucherSourceType sourceType,
            VoucherStatus status,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (root, query, criteriaBuilder) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (voucherType != null) {
                predicates.add(criteriaBuilder.equal(root.get("voucherType"), voucherType));
            }

            if (sourceType != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceType"), sourceType));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            } else {
                predicates.add(criteriaBuilder.notEqual(root.get("status"), VoucherStatus.CANCELLED));
            }

            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("voucherDate"), fromDate));
            }

            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("voucherDate"), toDate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<Long, LedgerMaster> lockRequestedLedgers(
            AccountingVoucherRequestDto request
    ) {
        List<Long> ledgerIds = request.getEntries().stream()
                .map(AccountingVoucherEntryRequestDto::getLedgerId)
                .distinct()
                .sorted()
                .toList();

        List<LedgerMaster> locked = ledgerMasterRepository
                .findAllByIdInAndDeletedFalseForUpdate(ledgerIds);

        Map<Long, LedgerMaster> byId = new LinkedHashMap<>();
        for (LedgerMaster ledger : locked) {
            byId.put(ledger.getId(), ledger);
        }

        for (Long ledgerId : ledgerIds) {
            if (!byId.containsKey(ledgerId)) {
                throw new ResourceNotFoundException(
                        "Ledger not found with ID: " + ledgerId,
                        "LEDGER_NOT_FOUND"
                );
            }
        }

        return byId;
    }

    private void validateVoucherRequest(AccountingVoucherRequestDto request) {

        if (request == null) {
            log.warn("Voucher validation failed. Request body is null");
            throw new ValidationException(
                    "Request body is required",
                    "ERR_REQUEST_REQUIRED"
            );
        }

        if (request.getVoucherType() == null) {
            log.warn("Voucher validation failed. Voucher type is null");
            throw new ValidationException(
                    "Voucher type is required",
                    "ERR_VOUCHER_TYPE_REQUIRED",
                    "voucherType"
            );
        }

        VoucherSourceType sourceType = request.getSourceType() == null
                ? VoucherSourceType.MANUAL
                : request.getSourceType();

        if (request.getSourceId() == null || request.getSourceId() <= 0) {
            log.warn(
                    "Voucher validation failed. Source ID missing/invalid. sourceType={}, sourceId={}",
                    sourceType,
                    request.getSourceId()
            );
            throw new ValidationException(
                    "Source ID must be greater than zero",
                    "ERR_SOURCE_ID_REQUIRED",
                    "sourceId"
            );
        }

        if (request.getEntries() == null || request.getEntries().size() < 2) {
            log.warn("Voucher validation failed. Minimum two entries required. entryCount={}",
                    request.getEntries() == null ? 0 : request.getEntries().size()
            );
            throw new ValidationException(
                    "At least two voucher entries are required",
                    "ERR_MIN_TWO_VOUCHER_ENTRIES_REQUIRED",
                    "entries"
            );
        }

        if (request.getVoucherType() == VoucherType.DEBIT_NOTE) {
            if (request.getPartyLedgerId() == null
                    || request.getPartyLedgerId() <= 0) {
                throw new ValidationException(
                        "Customer party ledger is required for a debit note",
                        "ERR_DEBIT_NOTE_PARTY_LEDGER_REQUIRED",
                        "partyLedgerId"
                );
            }

            boolean partyLedgerIncluded = request.getEntries()
                    .stream()
                    .anyMatch(entry -> Objects.equals(
                            entry.getLedgerId(),
                            request.getPartyLedgerId()
                    ));

            if (!partyLedgerIncluded) {
                throw new ValidationException(
                        "Debit-note party ledger must be included in voucher entries",
                        "ERR_DEBIT_NOTE_PARTY_ENTRY_REQUIRED",
                        "partyLedgerId"
                );
            }
        }

        BigDecimal totalDebit = zeroMoney();
        BigDecimal totalCredit = zeroMoney();

        for (int i = 0; i < request.getEntries().size(); i++) {

            AccountingVoucherEntryRequestDto entry = request.getEntries().get(i);

            if (entry.getLedgerId() == null || entry.getLedgerId() <= 0) {
                log.warn("Voucher validation failed. Invalid ledger ID at entry index {}. ledgerId={}", i, entry.getLedgerId());
                throw new ValidationException(
                        "Ledger ID is required at entry index " + i,
                        "ERR_LEDGER_ID_REQUIRED",
                        "entries[" + i + "].ledgerId"
                );
            }

            BigDecimal debit = safeMoney(entry.getDebitAmount());
            BigDecimal credit = safeMoney(entry.getCreditAmount());

            if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Voucher validation failed. Negative amount at entry index {}. debitAmount={}, creditAmount={}", i, debit, credit);
                throw new ValidationException(
                        "Debit/Credit cannot be negative at entry index " + i,
                        "ERR_NEGATIVE_AMOUNT_NOT_ALLOWED",
                        "entries[" + i + "]"
                );
            }

            if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("Voucher validation failed. Both debit and credit found at entry index {}. debitAmount={}, creditAmount={}", i, debit, credit);
                throw new ValidationException(
                        "One entry cannot have both debit and credit amount at entry index " + i,
                        "ERR_DEBIT_CREDIT_BOTH_NOT_ALLOWED",
                        "entries[" + i + "]"
                );
            }

            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("Voucher validation failed. Debit and credit both zero at entry index {}", i);
                throw new ValidationException(
                        "Either debit or credit amount is required at entry index " + i,
                        "ERR_DEBIT_OR_CREDIT_REQUIRED",
                        "entries[" + i + "]"
                );
            }

            totalDebit = totalDebit.add(debit).setScale(MONEY_SCALE, ROUNDING_MODE);
            totalCredit = totalCredit.add(credit).setScale(MONEY_SCALE, ROUNDING_MODE);
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            log.warn("Voucher validation failed. Debit and credit totals mismatch. totalDebit={}, totalCredit={}", totalDebit, totalCredit);
            throw new ValidationException(
                    "Total debit and total credit must be equal",
                    "ERR_DEBIT_CREDIT_NOT_EQUAL",
                    "entries"
            );
        }
    }

    private void validateDuplicateSource(VoucherSourceType sourceType, Long sourceId) {

        if (sourceType == null || sourceType == VoucherSourceType.MANUAL || sourceId == null) {
            log.debug("Skipping duplicate source validation. sourceType={}, sourceId={}", sourceType, sourceId);
            return;
        }

        boolean exists = accountingVoucherRepository.existsBySourceTypeAndSourceIdAndStatus(
                sourceType,
                sourceId,
                VoucherStatus.POSTED
        );

        if (exists) {
            log.warn("Duplicate posted voucher found for source. sourceType={}, sourceId={}", sourceType, sourceId);
            throw new ValidationException(
                    "Posted voucher already exists for source: " + sourceType + " ID: " + sourceId,
                    "ERR_VOUCHER_ALREADY_POSTED_FOR_SOURCE",
                    "sourceId"
            );
        }
    }

    private void updateLedgerBalancesForPostedVoucher(AccountingVoucher voucher) {

        if (voucher == null || voucher.getEntries() == null) {
            log.debug("Skipping ledger balance update because voucher or entries are null");
            return;
        }

        log.debug("Updating ledger balances for posted voucher. voucherId={}, voucherNumber={}, entryCount={}",
                voucher.getId(),
                voucher.getVoucherNumber(),
                voucher.getEntries().size()
        );

        for (AccountingVoucherEntry entry : voucher.getEntries()) {
            LedgerMaster ledger = entry.getLedger();

            updateLedgerBalance(
                    ledger,
                    safeMoney(entry.getDebitAmount()),
                    safeMoney(entry.getCreditAmount())
            );

            ledgerMasterRepository.save(ledger);
            log.debug("Ledger balance updated. ledgerId={}, ledgerName={}, currentBalance={}, currentBalanceType={}",
                    ledger.getId(),
                    ledger.getLedgerName(),
                    ledger.getCurrentBalance(),
                    ledger.getCurrentBalanceType()
            );
        }
    }

    private void reverseLedgerBalances(AccountingVoucher voucher) {

        if (voucher == null || voucher.getEntries() == null) {
            log.debug("Skipping ledger balance reverse because voucher or entries are null");
            return;
        }

        log.debug("Reversing ledger balances for voucher. voucherId={}, voucherNumber={}, entryCount={}",
                voucher.getId(),
                voucher.getVoucherNumber(),
                voucher.getEntries().size()
        );

        for (AccountingVoucherEntry entry : voucher.getEntries()) {
            LedgerMaster ledger = entry.getLedger();

            // Reverse means debit becomes credit and credit becomes debit
            updateLedgerBalance(
                    ledger,
                    safeMoney(entry.getCreditAmount()),
                    safeMoney(entry.getDebitAmount())
            );

            ledgerMasterRepository.save(ledger);
            log.debug("Ledger balance reversed. ledgerId={}, ledgerName={}, currentBalance={}, currentBalanceType={}",
                    ledger.getId(),
                    ledger.getLedgerName(),
                    ledger.getCurrentBalance(),
                    ledger.getCurrentBalanceType()
            );
        }
    }

    private void updateLedgerBalance(
            LedgerMaster ledger,
            BigDecimal debitAmount,
            BigDecimal creditAmount
    ) {
        BigDecimal currentBalance = safeMoney(ledger.getCurrentBalance());

        DebitCredit currentType = ledger.getCurrentBalanceType();
        log.debug("Calculating ledger balance. ledgerId={}, ledgerName={}, oldBalance={}, oldType={}, debitAmount={}, creditAmount={}",
                ledger.getId(),
                ledger.getLedgerName(),
                currentBalance,
                currentType,
                debitAmount,
                creditAmount
        );

        BigDecimal signedBalance;

        if (currentType == DebitCredit.CREDIT) {
            signedBalance = currentBalance.negate();
        } else {
            signedBalance = currentBalance;
        }

        BigDecimal newSignedBalance = signedBalance
                .add(safeMoney(debitAmount))
                .subtract(safeMoney(creditAmount))
                .setScale(MONEY_SCALE, ROUNDING_MODE);

        log.info(
                "[LEDGER-BALANCE-CALCULATION] ledgerId={} | ledgerName={} | ledgerType={} | "
                        + "signedBefore={} | debit={} | credit={} | signedAfter={}",
                ledger.getId(),
                ledger.getLedgerName(),
                ledger.getLedgerType(),
                signedBalance,
                safeMoney(debitAmount),
                safeMoney(creditAmount),
                newSignedBalance
        );

        if (newSignedBalance.compareTo(BigDecimal.ZERO) >= 0) {
            ledger.setCurrentBalance(newSignedBalance);
            ledger.setCurrentBalanceType(DebitCredit.DEBIT);
        } else {
            ledger.setCurrentBalance(newSignedBalance.abs());
            ledger.setCurrentBalanceType(DebitCredit.CREDIT);
        }

        log.debug("Ledger balance calculated. ledgerId={}, newBalance={}, newType={}",
                ledger.getId(),
                ledger.getCurrentBalance(),
                ledger.getCurrentBalanceType()
        );
    }

    private String generateVoucherNumber(
            VoucherType voucherType
    ) {
        String prefix = switch (voucherType) {
            case RECEIPT -> "RCP-VCH-";
            case SALES_INVOICE -> "INV-VCH-";
            case PURCHASE_INVOICE -> "PUR-VCH-";
            case DEBIT_NOTE -> "DN-VCH-";
            case ADVANCE_ADJUSTMENT -> "ADJ-VCH-";
            case CREDIT_NOTE -> "CN-VCH-";
            case REFUND -> "REF-VCH-";
            case JOURNAL -> "JRN-VCH-";
            case CONTRA -> "CON-VCH-";
            case PAYMENT -> "PAY-VCH-";
        };

        String voucherNumber;

        do {
            voucherNumber =
                    prefix
                            + LocalDate.now().getYear()
                            + "-"
                            + System.currentTimeMillis();

        } while (
                accountingVoucherRepository
                        .existsByVoucherNumberIgnoreCase(
                                voucherNumber
                        )
        );

        return voucherNumber;
    }


    private BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? zeroMoney()
                : value.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    private String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private AccountingVoucherResponseDto mapToResponse(
            AccountingVoucher voucher
    ) {
        if (voucher == null) {
            return null;
        }

        List<AccountingVoucherEntryResponseDto> entryResponses =
                mapVoucherEntries(voucher);

        LedgerMaster partyLedger = resolvePartyLedgerFromVoucher(voucher);

        Long clientCompanyId = voucher.getClientCompanyId();
        String clientCompanyName = voucher.getClientCompanyName();
        Long clientUnitId = voucher.getClientUnitId();
        String clientUnitName = voucher.getClientUnitName();

        // Fallback for older vouchers created before snapshot columns existed.
        if (partyLedger != null && partyLedger.getCompany() != null) {
            if (clientCompanyId == null) {
                clientCompanyId = partyLedger.getCompany().getId();
            }
            if (clientCompanyName == null) {
                clientCompanyName = partyLedger.getCompany().getName();
            }
        }

        if (partyLedger != null && partyLedger.getUnit() != null) {
            if (clientUnitId == null) {
                clientUnitId = partyLedger.getUnit().getId();
            }
            if (clientUnitName == null) {
                clientUnitName = partyLedger.getUnit().getUnitName();
            }
        }

        return AccountingVoucherResponseDto.builder()
                .id(voucher.getId())
                .voucherNumber(voucher.getVoucherNumber())
                .voucherType(voucher.getVoucherType())
                .voucherDate(voucher.getVoucherDate())
                .sourceType(voucher.getSourceType())
                .sourceId(voucher.getSourceId())
                .status(voucher.getStatus())
                .totalDebit(voucher.getTotalDebit())
                .totalCredit(voucher.getTotalCredit())
                .narration(voucher.getNarration())
                .projectId(voucher.getProjectId())
                .projectNo(voucher.getProjectNo())
                .projectName(voucher.getProjectName())
                .clientCompanyId(clientCompanyId)
                .clientCompanyName(clientCompanyName)
                .clientUnitId(clientUnitId)
                .clientUnitName(clientUnitName)
                .expensePaidBy(voucher.getExpensePaidBy())
                .partyLedgerId(
                        partyLedger != null ? partyLedger.getId() : null
                )
                .partyLedgerCode(
                        partyLedger != null
                                ? partyLedger.getLedgerCode()
                                : null
                )
                .partyLedgerName(
                        partyLedger != null
                                ? partyLedger.getLedgerName()
                                : null
                )
                .entries(entryResponses)
                .createdAt(voucher.getCreatedAt())
                .updatedAt(voucher.getUpdatedAt())
                .build();
    }

    private List<AccountingVoucherEntryResponseDto> mapVoucherEntries(
            AccountingVoucher voucher
    ) {
        if (voucher.getEntries() == null) {
            return List.of();
        }

        return voucher.getEntries()
                .stream()
                .sorted(Comparator.comparing(
                        entry -> entry.getDisplayOrder() != null
                                ? entry.getDisplayOrder()
                                : 0
                ))
                .map(this::mapEntryToResponse)
                .toList();
    }

    private LedgerMaster resolvePartyLedgerFromVoucher(
            AccountingVoucher voucher
    ) {
        if (voucher.getPartyLedger() != null) {
            return voucher.getPartyLedger();
        }

        if (voucher.getEntries() == null) {
            return null;
        }

        return voucher.getEntries()
                .stream()
                .map(AccountingVoucherEntry::getLedger)
                .filter(Objects::nonNull)
                .filter(ledger -> ledger.getLedgerType() == LedgerType.CUSTOMER)
                .findFirst()
                .orElse(null);
    }

    private AccountingVoucherEntryResponseDto mapEntryToResponse(AccountingVoucherEntry entry) {

        LedgerMaster ledger = entry.getLedger();

        return AccountingVoucherEntryResponseDto.builder()
                .id(entry.getId())
                .ledgerId(ledger != null ? ledger.getId() : null)
                .ledgerName(ledger != null ? ledger.getLedgerName() : null)
                .ledgerCode(ledger != null ? ledger.getLedgerCode() : null)
                .ledgerType(ledger != null ? ledger.getLedgerType() : null)
                .debitAmount(entry.getDebitAmount())
                .creditAmount(entry.getCreditAmount())
                .narration(entry.getNarration())
                .displayOrder(entry.getDisplayOrder())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsPostedVoucher(
            VoucherType voucherType,
            VoucherSourceType sourceType,
            Long sourceId
    ) {
        if (voucherType == null || sourceType == null || sourceId == null) {
            return false;
        }

        return accountingVoucherRepository
                .existsByVoucherTypeAndSourceTypeAndSourceIdAndStatusNot(
                        voucherType,
                        sourceType,
                        sourceId,
                        VoucherStatus.CANCELLED
                );
    }


}


