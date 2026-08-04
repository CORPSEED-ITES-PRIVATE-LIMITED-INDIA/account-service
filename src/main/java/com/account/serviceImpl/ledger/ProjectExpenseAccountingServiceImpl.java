package com.account.serviceImpl.ledger;

import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.ledger.AccountingVoucher;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import com.account.dto.GovernmentFeeFundTransferPostingRequestDto;
import com.account.dto.GovernmentFeeFundTransferPostingResponseDto;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.operationService.GovernmentFeePaidBy;
import com.account.dto.operationService.GovernmentFeePostingRequestDto;
import com.account.dto.operationService.GovernmentFeePostingResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
import com.account.repository.CompanyRepository;
import com.account.repository.CompanyUnitRepository;
import com.account.repository.UserRepository;
import com.account.repository.ledger.AccountingVoucherRepository;
import com.account.repository.ledger.LedgerGroupRepository;
import com.account.repository.ledger.LedgerMasterRepository;
import com.account.service.ledger.AccountingVoucherService;
import com.account.service.ledger.ProjectExpenseAccountingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectExpenseAccountingServiceImpl
        implements ProjectExpenseAccountingService {

    private static final int MONEY_SCALE = 3;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private static final String GOVERNMENT_FEE_EXPENSE_CODE =
            "LED-GOV-FEE-EXP";

    private static final String GOVERNMENT_FEE_PAYABLE_CODE =
            "LED-GOV-FEE-PAY";

    private static final String MAIN_CASH_LEDGER_CODE =
            "LED-CASH-MAIN";

    private static final Set<String> ALLOWED_PAYMENT_MODES =
            Set.of(
                    "CASH",
                    "CASH_DEPOSIT",
                    "CHEQUE",
                    "DEMAND_DRAFT",
                    "NEFT",
                    "RTGS",
                    "IMPS",
                    "UPI",
                    "CARD",
                    "BANK_TRANSFER",
                    "OTHER"
            );

    private static final Set<String> BANK_PAYMENT_MODES =
            Set.of(
                    "CASH_DEPOSIT",
                    "CHEQUE",
                    "DEMAND_DRAFT",
                    "NEFT",
                    "RTGS",
                    "IMPS",
                    "UPI",
                    "CARD",
                    "BANK_TRANSFER"
            );

    private final AccountingVoucherRepository accountingVoucherRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final CompanyRepository companyRepository;
    private final CompanyUnitRepository companyUnitRepository;
    private final UserRepository userRepository;
    private final AccountingVoucherService accountingVoucherService;

    /**
     * Both vouchers for CLIENT_TO_COMPANY are created inside this transaction.
     * If creation of either voucher fails, both voucher and ledger-balance
     * changes are rolled back by Spring.
     */
    @Override
    @Transactional
    public GovernmentFeePostingResponseDto postGovernmentFeeExpense(
            GovernmentFeePostingRequestDto request
    ) {

        validateRequest(request);

        log.info(
                "[GOVERNMENT-FEE-POSTING-START] operationExpenseId={} | projectNo={} | paidBy={} | amount={}",
                request.getOperationExpenseId(),
                request.getProjectNo(),
                request.getPaidBy(),
                request.getApprovedAmount()
        );

        return switch (request.getPaidBy()) {

            case CLIENT_TO_COMPANY ->
                    postClientFundedGovernmentFee(request);

            case COMPANY ->
                    postCompanyFundedGovernmentFee(request);

            case CLIENT_DIRECT, CLIENT ->
                    buildClientDirectSkippedResponse(request);
        };
    }

    private GovernmentFeePostingResponseDto postClientFundedGovernmentFee(
            GovernmentFeePostingRequestDto request
    ) {

        validateClientFundingDetails(request);

        Optional<AccountingVoucher> existingReceipt = findPostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_CLIENT_RECEIPT,
                request.getOperationExpenseId()
        );

        Optional<AccountingVoucher> existingJournal = findPostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                request.getOperationExpenseId()
        );

        if (existingReceipt.isPresent() && existingJournal.isPresent()) {
            return buildClientFundedResponse(
                    request,
                    "ALREADY_POSTED",
                    "Client receipt and government-fee accrual were already posted",
                    existingReceipt.get(),
                    existingJournal.get(),
                    null,
                    null,
                    null
            );
        }

        User approver = resolveApprover(request.getApprovedByUserId());

        LedgerMaster receivingLedger = resolveReceivingLedger(
                request,
                approver
        );

        LedgerMaster clientAdvanceLedger =
                getOrCreateClientGovernmentFeeAdvanceLedger(
                        request,
                        approver
                );

        LedgerMaster payableLedger = getOrCreateSystemLedger(
                LedgerType.GOVERNMENT_FEE_PAYABLE,
                LedgerGroupType.CURRENT_LIABILITIES,
                "Government Fee Payable",
                GOVERNMENT_FEE_PAYABLE_CODE,
                DebitCredit.CREDIT,
                approver
        );

        BigDecimal amount = money(request.getApprovedAmount());

        AccountingVoucher receiptVoucher = existingReceipt.orElseGet(() ->
                createClientReceiptVoucher(
                        request,
                        receivingLedger,
                        clientAdvanceLedger,
                        amount
                )
        );

        AccountingVoucher journalVoucher = existingJournal.orElseGet(() ->
                createClientAccrualJournal(
                        request,
                        clientAdvanceLedger,
                        payableLedger,
                        amount
                )
        );

        log.info(
                "[CLIENT-FUNDED-GOVERNMENT-FEE-POSTED] operationExpenseId={} | receiptVoucherId={} | receiptVoucherNumber={} | journalVoucherId={} | journalVoucherNumber={}",
                request.getOperationExpenseId(),
                receiptVoucher.getId(),
                receiptVoucher.getVoucherNumber(),
                journalVoucher.getId(),
                journalVoucher.getVoucherNumber()
        );

        return buildClientFundedResponse(
                request,
                "POSTED",
                "Client receipt and government-fee accrual posted successfully",
                receiptVoucher,
                journalVoucher,
                receivingLedger,
                clientAdvanceLedger,
                payableLedger
        );
    }

    private GovernmentFeePostingResponseDto postCompanyFundedGovernmentFee(
            GovernmentFeePostingRequestDto request
    ) {

        Optional<AccountingVoucher> existingJournal = findPostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                request.getOperationExpenseId()
        );

        if (existingJournal.isPresent()) {
            AccountingVoucher journal = existingJournal.get();

            return GovernmentFeePostingResponseDto.builder()
                    .postingStatus("ALREADY_POSTED")
                    .message("Company-funded government-fee accrual was already posted")
                    .operationExpenseId(request.getOperationExpenseId())
                    .journalVoucherId(journal.getId())
                    .journalVoucherNumber(journal.getVoucherNumber())
                    .voucherId(journal.getId())
                    .voucherNumber(journal.getVoucherNumber())
                    .postedAt(resolvePostedAt(journal))
                    .build();
        }

        User approver = resolveApprover(request.getApprovedByUserId());

        LedgerMaster expenseLedger = getOrCreateSystemLedger(
                LedgerType.GOVERNMENT_FEE_EXPENSE,
                LedgerGroupType.INDIRECT_EXPENSES,
                "Government Fee Expense",
                GOVERNMENT_FEE_EXPENSE_CODE,
                DebitCredit.DEBIT,
                approver
        );

        LedgerMaster payableLedger = getOrCreateSystemLedger(
                LedgerType.GOVERNMENT_FEE_PAYABLE,
                LedgerGroupType.CURRENT_LIABILITIES,
                "Government Fee Payable",
                GOVERNMENT_FEE_PAYABLE_CODE,
                DebitCredit.CREDIT,
                approver
        );

        BigDecimal amount = money(request.getApprovedAmount());

        AccountingVoucher journal = createCompanyAccrualJournal(
                request,
                expenseLedger,
                payableLedger,
                amount
        );

        log.info(
                "[COMPANY-FUNDED-GOVERNMENT-FEE-POSTED] operationExpenseId={} | journalVoucherId={} | journalVoucherNumber={}",
                request.getOperationExpenseId(),
                journal.getId(),
                journal.getVoucherNumber()
        );

        return GovernmentFeePostingResponseDto.builder()
                .postingStatus("POSTED")
                .message("Company-funded government-fee accrual posted successfully")
                .operationExpenseId(request.getOperationExpenseId())
                .journalVoucherId(journal.getId())
                .journalVoucherNumber(journal.getVoucherNumber())
                .governmentFeeExpenseLedgerId(expenseLedger.getId())
                .governmentFeePayableLedgerId(payableLedger.getId())
                .voucherId(journal.getId())
                .voucherNumber(journal.getVoucherNumber())
                .postedAt(resolvePostedAt(journal))
                .build();
    }

    private AccountingVoucher createClientReceiptVoucher(
            GovernmentFeePostingRequestDto request,
            LedgerMaster receivingLedger,
            LedgerMaster clientAdvanceLedger,
            BigDecimal amount
    ) {

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.RECEIPT)
                        .voucherDate(request.getClientPaymentDate())
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_CLIENT_RECEIPT
                        )
                        .sourceId(request.getOperationExpenseId())
                        .narration(
                                "Client government-fee funding received for project "
                                        + safeProjectNumber(request)
                                        + ", reference "
                                        + clean(request.getClientPaymentReference())
                        )
                        .entries(List.of(
                                debitEntry(
                                        receivingLedger.getId(),
                                        amount,
                                        "Client government-fee funding received"
                                ),
                                creditEntry(
                                        clientAdvanceLedger.getId(),
                                        amount,
                                        "Client government-fee advance recognised"
                                )
                        ))
                        .build();

        AccountingVoucherResponseDto response =
                accountingVoucherService.createVoucher(voucherRequest);

        return getCreatedVoucher(response.getId());
    }

    private AccountingVoucher createClientAccrualJournal(
            GovernmentFeePostingRequestDto request,
            LedgerMaster clientAdvanceLedger,
            LedgerMaster payableLedger,
            BigDecimal amount
    ) {

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.JOURNAL)
                        .voucherDate(resolvePostingDate(request))
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL
                        )
                        .sourceId(request.getOperationExpenseId())
                        .narration(buildNarration(request))
                        .entries(List.of(
                                debitEntry(
                                        clientAdvanceLedger.getId(),
                                        amount,
                                        "Client advance earmarked for government fee"
                                ),
                                creditEntry(
                                        payableLedger.getId(),
                                        amount,
                                        "Government-fee payable created"
                                )
                        ))
                        .build();

        AccountingVoucherResponseDto response =
                accountingVoucherService.createVoucher(voucherRequest);

        return getCreatedVoucher(response.getId());
    }

    private AccountingVoucher createCompanyAccrualJournal(
            GovernmentFeePostingRequestDto request,
            LedgerMaster expenseLedger,
            LedgerMaster payableLedger,
            BigDecimal amount
    ) {

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.JOURNAL)
                        .voucherDate(resolvePostingDate(request))
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL
                        )
                        .sourceId(request.getOperationExpenseId())
                        .narration(buildNarration(request))
                        .entries(List.of(
                                debitEntry(
                                        expenseLedger.getId(),
                                        amount,
                                        "Government-fee expense booked"
                                ),
                                creditEntry(
                                        payableLedger.getId(),
                                        amount,
                                        "Government-fee payable created"
                                )
                        ))
                        .build();

        AccountingVoucherResponseDto response =
                accountingVoucherService.createVoucher(voucherRequest);

        return getCreatedVoucher(response.getId());
    }

    private AccountingVoucherEntryRequestDto debitEntry(
            Long ledgerId,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledgerId)
                .debitAmount(amount)
                .creditAmount(zero())
                .narration(narration)
                .build();
    }

    private AccountingVoucherEntryRequestDto creditEntry(
            Long ledgerId,
            BigDecimal amount,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledgerId)
                .debitAmount(zero())
                .creditAmount(amount)
                .narration(narration)
                .build();
    }

    private Optional<AccountingVoucher> findPostedVoucher(
            VoucherSourceType sourceType,
            Long operationExpenseId
    ) {
        return accountingVoucherRepository
                .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                        sourceType,
                        operationExpenseId,
                        VoucherStatus.POSTED
                );
    }

    private AccountingVoucher getCreatedVoucher(Long voucherId) {
        return accountingVoucherRepository.findById(voucherId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Created accounting voucher was not found with ID: " + voucherId,
                        "ACCOUNTING_VOUCHER_NOT_FOUND"
                ));
    }

    private LedgerMaster resolveReceivingLedger(
            GovernmentFeePostingRequestDto request,
            User approver
    ) {

        String paymentMode = normalizePaymentMode(
                request.getClientPaymentMode()
        );

        if ("CASH".equals(paymentMode)
                && request.getClientPaymentBankLedgerId() == null) {

            return getOrCreateSystemLedger(
                    LedgerType.CASH,
                    LedgerGroupType.CASH_IN_HAND,
                    "Cash in Hand",
                    MAIN_CASH_LEDGER_CODE,
                    DebitCredit.DEBIT,
                    approver
            );
        }

        Long ledgerId = request.getClientPaymentBankLedgerId();

        if (ledgerId == null || ledgerId <= 0) {
            throw new ValidationException(
                    "Receiving ledger ID is required for payment mode " + paymentMode,
                    "ERR_RECEIVING_LEDGER_REQUIRED",
                    "clientPaymentBankLedgerId"
            );
        }

        LedgerMaster ledger = ledgerMasterRepository
                .findByIdAndDeletedFalse(ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Receiving ledger not found with ID: " + ledgerId,
                        "LEDGER_NOT_FOUND"
                ));

        if (!ledger.isActive()) {
            throw new ValidationException(
                    "Receiving ledger is inactive: " + ledger.getLedgerName(),
                    "ERR_RECEIVING_LEDGER_INACTIVE",
                    "clientPaymentBankLedgerId"
            );
        }

        Set<LedgerType> allowedTypes = "CASH".equals(paymentMode)
                ? EnumSet.of(LedgerType.CASH)
                : "OTHER".equals(paymentMode)
                ? EnumSet.of(
                LedgerType.BANK,
                LedgerType.CASH,
                LedgerType.PAYMENT_GATEWAY
        )
                : EnumSet.of(
                LedgerType.BANK,
                LedgerType.PAYMENT_GATEWAY
        );

        if (!allowedTypes.contains(ledger.getLedgerType())) {
            throw new ValidationException(
                    "Invalid receiving ledger type "
                            + ledger.getLedgerType()
                            + " for payment mode "
                            + paymentMode,
                    "ERR_INVALID_RECEIVING_LEDGER_TYPE",
                    "clientPaymentBankLedgerId"
            );
        }

        return ledger;
    }

    private LedgerMaster getOrCreateClientGovernmentFeeAdvanceLedger(
            GovernmentFeePostingRequestDto request,
            User approver
    ) {

        Long companyId = request.getClientCompanyId();
        Long unitId = request.getClientUnitId();

        String ledgerCode = "LED-GOV-ADV-C" + companyId + "-U" + unitId;

        Optional<LedgerMaster> existing = ledgerMasterRepository
                .findByLedgerCodeIgnoreCaseAndDeletedFalse(ledgerCode);

        if (existing.isPresent()) {
            return activateIfRequired(existing.get(), approver);
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client company not found with ID: " + companyId,
                        "COMPANY_NOT_FOUND"
                ));

        CompanyUnit unit = companyUnitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Client unit not found with ID: " + unitId,
                        "COMPANY_UNIT_NOT_FOUND"
                ));

        if (unit.getCompany() == null
                || unit.getCompany().getId() == null
                || !companyId.equals(unit.getCompany().getId())) {

            throw new ValidationException(
                    "Selected client unit does not belong to selected company",
                    "ERR_UNIT_COMPANY_MISMATCH",
                    "clientUnitId"
            );
        }

        LedgerGroup group = getLedgerGroup(
                LedgerGroupType.CURRENT_LIABILITIES
        );

        String companyName = firstNonBlank(
                request.getClientCompanyName(),
                company.getName(),
                "Company-" + companyId
        );

        String unitName = firstNonBlank(
                request.getClientUnitName(),
                unit.getUnitName(),
                "Unit-" + unitId
        );

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerName(
                truncate(
                        companyName + " - " + unitName + " (Govt Fee Advance)",
                        255
                )
        );
        ledger.setLedgerCode(ledgerCode);
        ledger.setLedgerType(
                LedgerType.GOVERNMENT_FEE_CLIENT_ADVANCE
        );
        ledger.setLedgerGroup(group);
        ledger.setCompany(company);
        ledger.setUnit(unit);
        ledger.setOpeningBalance(zero());
        ledger.setOpeningBalanceType(DebitCredit.CREDIT);
        ledger.setCurrentBalance(zero());
        ledger.setCurrentBalanceType(DebitCredit.CREDIT);
        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        if (approver != null) {
            ledger.setCreatedBy(approver);
            ledger.setUpdatedBy(approver);
        }

        try {
            return ledgerMasterRepository.saveAndFlush(ledger);
        } catch (DataIntegrityViolationException exception) {
            return ledgerMasterRepository
                    .findByLedgerCodeIgnoreCaseAndDeletedFalse(ledgerCode)
                    .map(value -> activateIfRequired(value, approver))
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerMaster getOrCreateSystemLedger(
            LedgerType ledgerType,
            LedgerGroupType groupType,
            String ledgerName,
            String ledgerCode,
            DebitCredit normalBalance,
            User approver
    ) {

        Optional<LedgerMaster> byCode = ledgerMasterRepository
                .findByLedgerCodeIgnoreCaseAndDeletedFalse(ledgerCode);

        if (byCode.isPresent()) {
            return activateIfRequired(byCode.get(), approver);
        }

        Optional<LedgerMaster> byType = ledgerMasterRepository
                .findByLedgerTypeAndDeletedFalse(ledgerType);

        if (byType.isPresent()) {
            return activateIfRequired(byType.get(), approver);
        }

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerName(ledgerName);
        ledger.setLedgerCode(ledgerCode);
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(getLedgerGroup(groupType));
        ledger.setOpeningBalance(zero());
        ledger.setOpeningBalanceType(normalBalance);
        ledger.setCurrentBalance(zero());
        ledger.setCurrentBalanceType(normalBalance);
        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        if (approver != null) {
            ledger.setCreatedBy(approver);
            ledger.setUpdatedBy(approver);
        }

        try {
            return ledgerMasterRepository.saveAndFlush(ledger);
        } catch (DataIntegrityViolationException exception) {
            return ledgerMasterRepository
                    .findByLedgerCodeIgnoreCaseAndDeletedFalse(ledgerCode)
                    .map(value -> activateIfRequired(value, approver))
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerMaster activateIfRequired(
            LedgerMaster ledger,
            User approver
    ) {
        if (ledger.isActive()) {
            return ledger;
        }

        ledger.setActive(true);
        ledger.setUpdatedBy(approver);
        return ledgerMasterRepository.save(ledger);
    }

    private LedgerGroup getLedgerGroup(LedgerGroupType groupType) {
        return ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(groupType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ledger group not found for type: " + groupType,
                        "LEDGER_GROUP_NOT_FOUND"
                ));
    }

    private User resolveApprover(Long userId) {
        if (userId == null) {
            return null;
        }

        return userRepository.findById(userId)
                .orElseGet(() -> {
                    log.warn(
                            "[ACCOUNT-APPROVER-NOT-FOUND] userId={}",
                            userId
                    );
                    return null;
                });
    }

    private GovernmentFeePostingResponseDto buildClientFundedResponse(
            GovernmentFeePostingRequestDto request,
            String status,
            String message,
            AccountingVoucher receipt,
            AccountingVoucher journal,
            LedgerMaster receivingLedger,
            LedgerMaster clientAdvanceLedger,
            LedgerMaster payableLedger
    ) {
        return GovernmentFeePostingResponseDto.builder()
                .postingStatus(status)
                .message(message)
                .operationExpenseId(request.getOperationExpenseId())
                .receiptVoucherId(receipt.getId())
                .receiptVoucherNumber(receipt.getVoucherNumber())
                .journalVoucherId(journal.getId())
                .journalVoucherNumber(journal.getVoucherNumber())
                .receivingBankLedgerId(
                        receivingLedger != null
                                ? receivingLedger.getId()
                                : request.getClientPaymentBankLedgerId()
                )
                .clientAdvanceLedgerId(
                        clientAdvanceLedger != null
                                ? clientAdvanceLedger.getId()
                                : null
                )
                .governmentFeePayableLedgerId(
                        payableLedger != null
                                ? payableLedger.getId()
                                : null
                )
                .voucherId(journal.getId())
                .voucherNumber(journal.getVoucherNumber())
                .postedAt(resolvePostedAt(journal))
                .build();
    }

    private GovernmentFeePostingResponseDto buildClientDirectSkippedResponse(
            GovernmentFeePostingRequestDto request
    ) {
        return GovernmentFeePostingResponseDto.builder()
                .postingStatus("SKIPPED_CLIENT_DIRECT")
                .message("Client paid the government portal directly. No voucher was created.")
                .operationExpenseId(request.getOperationExpenseId())
                .postedAt(LocalDateTime.now())
                .build();
    }

    private void validateRequest(GovernmentFeePostingRequestDto request) {

        if (request == null) {
            throw new ValidationException(
                    "Government-fee posting request is required",
                    "ERR_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (request.getOperationExpenseId() == null
                || request.getOperationExpenseId() <= 0) {
            throw new ValidationException(
                    "Operation expense ID must be greater than zero",
                    "ERR_OPERATION_EXPENSE_ID_REQUIRED",
                    "operationExpenseId"
            );
        }

        if (request.getPaidBy() == null) {
            throw new ValidationException(
                    "Paid by is required",
                    "ERR_PAID_BY_REQUIRED",
                    "paidBy"
            );
        }

        if (request.getApprovedAmount() == null
                || request.getApprovedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Approved amount must be greater than zero",
                    "ERR_APPROVED_AMOUNT_INVALID",
                    "approvedAmount"
            );
        }

        if (!"GOVERNMENT_FEE".equalsIgnoreCase(
                clean(request.getExpenseCategory())
        )) {
            throw new ValidationException(
                    "Only GOVERNMENT_FEE can be posted through this API",
                    "ERR_INVALID_EXPENSE_CATEGORY",
                    "expenseCategory"
            );
        }

        if (!"INR".equalsIgnoreCase(clean(request.getCurrencyCode()))) {
            throw new ValidationException(
                    "Government-fee accounting currently supports INR only",
                    "ERR_UNSUPPORTED_CURRENCY",
                    "currencyCode"
            );
        }

        if (request.getExpenseDate() != null
                && request.getExpenseDate().isAfter(LocalDate.now())) {
            throw new ValidationException(
                    "Expense posting date cannot be in the future",
                    "ERR_FUTURE_POSTING_DATE",
                    "expenseDate"
            );
        }
    }

    private void validateClientFundingDetails(
            GovernmentFeePostingRequestDto request
    ) {

        if (request.getClientCompanyId() == null
                || request.getClientCompanyId() <= 0) {
            throw new ValidationException(
                    "Client company ID is required for client-funded government fee",
                    "ERR_CLIENT_COMPANY_REQUIRED",
                    "clientCompanyId"
            );
        }

        if (request.getClientUnitId() == null
                || request.getClientUnitId() <= 0) {
            throw new ValidationException(
                    "Client unit ID is required for client-funded government fee",
                    "ERR_CLIENT_UNIT_REQUIRED",
                    "clientUnitId"
            );
        }

        String mode = normalizePaymentMode(request.getClientPaymentMode());

        if (request.getClientPaymentDate() == null) {
            throw new ValidationException(
                    "Client payment date is required",
                    "ERR_CLIENT_PAYMENT_DATE_REQUIRED",
                    "clientPaymentDate"
            );
        }

        if (request.getClientPaymentDate().isAfter(LocalDate.now())) {
            throw new ValidationException(
                    "Client payment date cannot be in the future",
                    "ERR_FUTURE_CLIENT_PAYMENT_DATE",
                    "clientPaymentDate"
            );
        }

        requireText(
                request.getClientPaymentReference(),
                "Client payment reference is required",
                "ERR_CLIENT_PAYMENT_REFERENCE_REQUIRED",
                "clientPaymentReference"
        );

        requireText(
                request.getClientPaymentProofUrl(),
                "Client payment proof is required",
                "ERR_CLIENT_PAYMENT_PROOF_REQUIRED",
                "clientPaymentProofUrl"
        );

        if ((BANK_PAYMENT_MODES.contains(mode) || "OTHER".equals(mode))
                && (request.getClientPaymentBankLedgerId() == null
                || request.getClientPaymentBankLedgerId() <= 0)) {

            throw new ValidationException(
                    "Receiving ledger is required for payment mode " + mode,
                    "ERR_RECEIVING_LEDGER_REQUIRED",
                    "clientPaymentBankLedgerId"
            );
        }
    }

    private String normalizePaymentMode(String value) {
        String mode = clean(value);

        if (mode == null) {
            throw new ValidationException(
                    "Client payment mode is required",
                    "ERR_CLIENT_PAYMENT_MODE_REQUIRED",
                    "clientPaymentMode"
            );
        }

        mode = mode
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');

        if (!ALLOWED_PAYMENT_MODES.contains(mode)) {
            throw new ValidationException(
                    "Unsupported client payment mode: " + mode,
                    "ERR_INVALID_CLIENT_PAYMENT_MODE",
                    "clientPaymentMode"
            );
        }

        return mode;
    }

    private String requireText(
            String value,
            String message,
            String errorCode,
            String field
    ) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new ValidationException(message, errorCode, field);
        }
        return cleaned;
    }

    private LocalDate resolvePostingDate(
            GovernmentFeePostingRequestDto request
    ) {
        return request.getExpenseDate() != null
                ? request.getExpenseDate()
                : LocalDate.now();
    }

    private String buildNarration(
            GovernmentFeePostingRequestDto request
    ) {
        String supplied = clean(request.getNarration());
        if (supplied != null) {
            return supplied;
        }

        return "Government fee approved for project "
                + safeProjectNumber(request)
                + ", Operation expense ID "
                + request.getOperationExpenseId();
    }

    private String safeProjectNumber(
            GovernmentFeePostingRequestDto request
    ) {
        String projectNo = clean(request.getProjectNo());
        return projectNo != null
                ? projectNo
                : String.valueOf(request.getProjectId());
    }

    private LocalDateTime resolvePostedAt(AccountingVoucher voucher) {
        return voucher.getCreatedAt() != null
                ? voucher.getCreatedAt()
                : LocalDateTime.now();
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = clean(value);
            if (cleaned != null) {
                return cleaned;
            }
        }
        return null;
    }

    private String clean(String value) {
        return value == null || value.trim().isEmpty()
                ? null
                : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }


    @Override
    @Transactional
    public GovernmentFeeFundTransferPostingResponseDto
    postGovernmentFeeFundTransfer(
            GovernmentFeeFundTransferPostingRequestDto request
    ) {

        validateFundTransferRequest(request);

        Optional<AccountingVoucher> existingVoucher =
                accountingVoucherRepository
                        .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                                VoucherSourceType
                                        .PROJECT_EXPENSE_FUND_TRANSFER,
                                request.getOperationExpenseId(),
                                VoucherStatus.POSTED
                        );

        if (existingVoucher.isPresent()) {

            AccountingVoucher voucher =
                    existingVoucher.get();

            return GovernmentFeeFundTransferPostingResponseDto
                    .builder()
                    .postingStatus("ALREADY_POSTED")
                    .message("Fund transfer was already posted")
                    .operationExpenseId(
                            request.getOperationExpenseId()
                    )
                    .contraVoucherId(voucher.getId())
                    .contraVoucherNumber(
                            voucher.getVoucherNumber()
                    )
                    .fromBankLedgerId(
                            request.getFromBankLedgerId()
                    )
                    .toBankLedgerId(
                            request.getToBankLedgerId()
                    )
                    .postedAt(
                            voucher.getCreatedAt() != null
                                    ? voucher.getCreatedAt()
                                    : LocalDateTime.now()
                    )
                    .build();
        }

        LedgerMaster fromBank =
                validateActiveBankLedger(
                        request.getFromBankLedgerId(),
                        "fromBankLedgerId"
                );

        LedgerMaster toBank =
                validateActiveBankLedger(
                        request.getToBankLedgerId(),
                        "toBankLedgerId"
                );

        BigDecimal amount =
                request.getAmount()
                        .setScale(
                                3,
                                RoundingMode.HALF_UP
                        );

        AccountingVoucherEntryRequestDto debitAxis =
                AccountingVoucherEntryRequestDto
                        .builder()
                        .ledgerId(toBank.getId())
                        .debitAmount(amount)
                        .creditAmount(zero())
                        .narration(
                                "Government-fee funds received from "
                                        + fromBank.getLedgerName()
                        )
                        .build();

        AccountingVoucherEntryRequestDto creditHdfc =
                AccountingVoucherEntryRequestDto
                        .builder()
                        .ledgerId(fromBank.getId())
                        .debitAmount(zero())
                        .creditAmount(amount)
                        .narration(
                                "Government-fee funds transferred to "
                                        + toBank.getLedgerName()
                        )
                        .build();

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto
                        .builder()
                        .voucherType(VoucherType.CONTRA)
                        .voucherDate(
                                request.getTransferDate()
                        )
                        .sourceType(
                                VoucherSourceType
                                        .PROJECT_EXPENSE_FUND_TRANSFER
                        )
                        .sourceId(
                                request.getOperationExpenseId()
                        )
                        .narration(
                                request.getNarration()
                        )
                        .entries(
                                List.of(
                                        debitAxis,
                                        creditHdfc
                                )
                        )
                        .build();

        AccountingVoucherResponseDto voucher =
                accountingVoucherService
                        .createVoucher(voucherRequest);

        return GovernmentFeeFundTransferPostingResponseDto
                .builder()
                .postingStatus("POSTED")
                .message(
                        "Government-fee fund transfer posted successfully"
                )
                .operationExpenseId(
                        request.getOperationExpenseId()
                )
                .contraVoucherId(voucher.getId())
                .contraVoucherNumber(
                        voucher.getVoucherNumber()
                )
                .fromBankLedgerId(fromBank.getId())
                .toBankLedgerId(toBank.getId())
                .postedAt(LocalDateTime.now())
                .build();
    }

    private LedgerMaster validateActiveBankLedger(
            Long ledgerId,
            String field
    ) {

        LedgerMaster ledger =
                ledgerMasterRepository
                        .findByIdAndDeletedFalse(ledgerId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Bank ledger not found with ID: "
                                                + ledgerId,
                                        "LEDGER_NOT_FOUND"
                                )
                        );

        if (!ledger.isActive()) {
            throw new ValidationException(
                    "Bank ledger is inactive: "
                            + ledger.getLedgerName(),
                    "ERR_BANK_LEDGER_INACTIVE",
                    field
            );
        }

        if (ledger.getLedgerType() != LedgerType.BANK) {
            throw new ValidationException(
                    "Ledger is not a bank ledger: "
                            + ledger.getLedgerName(),
                    "ERR_INVALID_BANK_LEDGER",
                    field
            );
        }

        return ledger;
    }

    private void validateFundTransferRequest(
            GovernmentFeeFundTransferPostingRequestDto request
    ) {

        if (request == null) {
            throw new ValidationException(
                    "Fund-transfer request is required",
                    "ERR_REQUEST_REQUIRED",
                    "request"
            );
        }

        if (request.getOperationExpenseId() == null) {
            throw new ValidationException(
                    "Operation expense ID is required",
                    "ERR_OPERATION_EXPENSE_ID_REQUIRED",
                    "operationExpenseId"
            );
        }

        if (request.getFromBankLedgerId() == null
                || request.getToBankLedgerId() == null) {

            throw new ValidationException(
                    "Both bank ledger IDs are required",
                    "ERR_BANK_LEDGERS_REQUIRED",
                    "bankLedgerId"
            );
        }

        if (request.getFromBankLedgerId().equals(
                request.getToBankLedgerId()
        )) {

            throw new ValidationException(
                    "From bank and To bank cannot be the same",
                    "ERR_SAME_BANK_TRANSFER",
                    "toBankLedgerId"
            );
        }

        if (request.getAmount() == null
                || request.getAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw new ValidationException(
                    "Transfer amount must be greater than zero",
                    "ERR_INVALID_TRANSFER_AMOUNT",
                    "amount"
            );
        }

        if (request.getTransferDate() == null) {
            throw new ValidationException(
                    "Transfer date is required",
                    "ERR_TRANSFER_DATE_REQUIRED",
                    "transferDate"
            );
        }

        if (request.getTransferDate().isAfter(
                LocalDate.now()
        )) {

            throw new ValidationException(
                    "Transfer date cannot be in the future",
                    "ERR_FUTURE_TRANSFER_DATE",
                    "transferDate"
            );
        }
    }
}
