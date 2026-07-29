package com.account.serviceImpl.ledger;

import com.account.domain.User;
import com.account.domain.ledger.*;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.operationService.GovernmentFeePaidBy;
import com.account.dto.operationService.GovernmentFeePostingRequestDto;
import com.account.dto.operationService.GovernmentFeePostingResponseDto;
import com.account.exception.ResourceNotFoundException;
import com.account.exception.ValidationException;
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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectExpenseAccountingServiceImpl
        implements ProjectExpenseAccountingService {

    private static final String GOVERNMENT_FEE_EXPENSE_CODE =
            "LED-GOV-FEE-EXP";

    private static final String GOVERNMENT_FEE_PAYABLE_CODE =
            "LED-GOV-FEE-PAY";

    private final AccountingVoucherRepository
            accountingVoucherRepository;

    private final LedgerMasterRepository
            ledgerMasterRepository;

    private final LedgerGroupRepository
            ledgerGroupRepository;

    private final UserRepository userRepository;

    private final AccountingVoucherService
            accountingVoucherService;

    @Override
    @Transactional
    public GovernmentFeePostingResponseDto
    postGovernmentFeeExpense(
            GovernmentFeePostingRequestDto request
    ) {

        validateRequest(request);

        /*
         * Client paid directly.
         * No accounting transaction belongs to the company.
         */
        if (request.getPaidBy() ==
                GovernmentFeePaidBy.CLIENT) {

            return GovernmentFeePostingResponseDto
                    .builder()
                    .postingStatus("SKIPPED_CLIENT_PAID")
                    .message(
                            "Client paid the government fee directly. No voucher created."
                    )
                    .operationExpenseId(
                            request.getOperationExpenseId()
                    )
                    .postedAt(LocalDateTime.now())
                    .build();
        }

        /*
         * Idempotency:
         * return existing posted voucher instead of creating duplicate.
         */
        Optional<AccountingVoucher> existingVoucher =
                accountingVoucherRepository
                        .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                                VoucherSourceType.PROJECT_EXPENSE,
                                request.getOperationExpenseId(),
                                VoucherStatus.POSTED
                        );

        if (existingVoucher.isPresent()) {

            AccountingVoucher voucher =
                    existingVoucher.get();

            return GovernmentFeePostingResponseDto
                    .builder()
                    .postingStatus("ALREADY_POSTED")
                    .message(
                            "Government fee voucher was already posted"
                    )
                    .operationExpenseId(
                            request.getOperationExpenseId()
                    )
                    .voucherId(voucher.getId())
                    .voucherNumber(
                            voucher.getVoucherNumber()
                    )
                    .postedAt(
                            voucher.getCreatedAt() != null
                                    ? voucher.getCreatedAt()
                                    : LocalDateTime.now()
                    )
                    .build();
        }

        User approver = resolveApprover(
                request.getApprovedByUserId()
        );

        LedgerMaster governmentFeeExpenseLedger =
                getOrCreateGovernmentFeeLedger(
                        LedgerType.GOVERNMENT_FEE_EXPENSE,
                        LedgerGroupType.INDIRECT_EXPENSES,
                        "Government Fee Expense",
                        GOVERNMENT_FEE_EXPENSE_CODE,
                        DebitCredit.DEBIT,
                        approver
                );

        LedgerMaster governmentFeePayableLedger =
                getOrCreateGovernmentFeeLedger(
                        LedgerType.GOVERNMENT_FEE_PAYABLE,
                        LedgerGroupType.CURRENT_LIABILITIES,
                        "Government Fee Payable",
                        GOVERNMENT_FEE_PAYABLE_CODE,
                        DebitCredit.CREDIT,
                        approver
                );

        BigDecimal amount = money(
                request.getApprovedAmount()
        );

        AccountingVoucherEntryRequestDto debitEntry =
                AccountingVoucherEntryRequestDto
                        .builder()
                        .ledgerId(
                                governmentFeeExpenseLedger.getId()
                        )
                        .debitAmount(amount)
                        .creditAmount(zero())
                        .narration(
                                "Government fee expense booked for project "
                                        + safeProjectNumber(request)
                        )
                        .build();

        AccountingVoucherEntryRequestDto creditEntry =
                AccountingVoucherEntryRequestDto
                        .builder()
                        .ledgerId(
                                governmentFeePayableLedger.getId()
                        )
                        .debitAmount(zero())
                        .creditAmount(amount)
                        .narration(
                                "Government fee payable created for project "
                                        + safeProjectNumber(request)
                        )
                        .build();

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto
                        .builder()
                        .voucherType(VoucherType.JOURNAL)
                        .voucherDate(
                                request.getExpenseDate() != null
                                        ? request.getExpenseDate()
                                        : LocalDate.now()
                        )
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE
                        )
                        .sourceId(
                                request.getOperationExpenseId()
                        )
                        .narration(
                                buildNarration(request)
                        )
                        .entries(
                                List.of(
                                        debitEntry,
                                        creditEntry
                                )
                        )
                        .build();

        AccountingVoucherResponseDto voucherResponse =
                accountingVoucherService
                        .createVoucher(voucherRequest);

        log.info(
                "Government fee voucher posted | operationExpenseId={} | "
                        + "projectNo={} | amount={} | voucherId={} | voucherNumber={}",
                request.getOperationExpenseId(),
                request.getProjectNo(),
                amount,
                voucherResponse.getId(),
                voucherResponse.getVoucherNumber()
        );

        return GovernmentFeePostingResponseDto
                .builder()
                .postingStatus("POSTED")
                .message(
                        "Government fee expense posted successfully"
                )
                .operationExpenseId(
                        request.getOperationExpenseId()
                )
                .voucherId(
                        voucherResponse.getId()
                )
                .voucherNumber(
                        voucherResponse.getVoucherNumber()
                )
                .governmentFeeExpenseLedgerId(
                        governmentFeeExpenseLedger.getId()
                )
                .governmentFeePayableLedgerId(
                        governmentFeePayableLedger.getId()
                )
                .postedAt(LocalDateTime.now())
                .build();
    }

    private LedgerMaster getOrCreateGovernmentFeeLedger(
            LedgerType ledgerType,
            LedgerGroupType groupType,
            String ledgerName,
            String ledgerCode,
            DebitCredit normalBalanceType,
            User createdBy
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
                existing.setUpdatedBy(createdBy);

                return ledgerMasterRepository.save(
                        existing
                );
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
                ledgerGroupRepository
                        .findByGroupTypeAndDeletedFalse(
                                groupType
                        )
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Ledger group not found for type: "
                                                + groupType,
                                        "LEDGER_GROUP_NOT_FOUND"
                                )
                        );

        LedgerMaster ledger = new LedgerMaster();

        ledger.setLedgerName(ledgerName);
        ledger.setLedgerCode(ledgerCode);
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(ledgerGroup);

        ledger.setOpeningBalance(zero());
        ledger.setOpeningBalanceType(
                normalBalanceType
        );

        ledger.setCurrentBalance(zero());
        ledger.setCurrentBalanceType(
                normalBalanceType
        );

        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        if (createdBy != null) {
            ledger.setCreatedBy(createdBy);
            ledger.setUpdatedBy(createdBy);
        }

        try {
            return ledgerMasterRepository.saveAndFlush(
                    ledger
            );
        } catch (DataIntegrityViolationException exception) {

            /*
             * Another request may have created the same
             * system ledger concurrently.
             */
            return ledgerMasterRepository
                    .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                            ledgerCode
                    )
                    .orElseThrow(() -> exception);
        }
    }

    private User resolveApprover(Long userId) {

        if (userId == null) {
            return null;
        }

        return userRepository
                .findById(userId)
                .orElseGet(() -> {
                    log.warn(
                            "Approver was not found in Account Service | userId={}",
                            userId
                    );
                    return null;
                });
    }

    private void validateRequest(
            GovernmentFeePostingRequestDto request
    ) {

        if (request == null) {
            throw new ValidationException(
                    "Government fee posting request is required",
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

        if (request.getPaidBy() == null) {
            throw new ValidationException(
                    "Paid by is required",
                    "ERR_PAID_BY_REQUIRED",
                    "paidBy"
            );
        }

        if (request.getPaidBy() ==
                GovernmentFeePaidBy.COMPANY) {

            if (request.getApprovedAmount() == null
                    || request.getApprovedAmount()
                    .compareTo(BigDecimal.ZERO) <= 0) {

                throw new ValidationException(
                        "Approved amount must be greater than zero",
                        "ERR_APPROVED_AMOUNT_INVALID",
                        "approvedAmount"
                );
            }
        }

        if (request.getCurrencyCode() != null
                && !"INR".equalsIgnoreCase(
                request.getCurrencyCode()
        )) {

            throw new ValidationException(
                    "Government fee accounting currently supports INR only",
                    "ERR_UNSUPPORTED_CURRENCY",
                    "currencyCode"
            );
        }
    }

    private String buildNarration(
            GovernmentFeePostingRequestDto request
    ) {

        String suppliedNarration =
                clean(request.getNarration());

        if (suppliedNarration != null) {
            return suppliedNarration;
        }

        return "Government fee approved for project "
                + safeProjectNumber(request)
                + ", Operation expense ID "
                + request.getOperationExpenseId();
    }

    private String safeProjectNumber(
            GovernmentFeePostingRequestDto request
    ) {

        return request.getProjectNo() != null
                && !request.getProjectNo()
                .trim()
                .isEmpty()
                ? request.getProjectNo().trim()
                : String.valueOf(
                request.getProjectId()
        );
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(
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

    private String clean(String value) {
        return value == null
                || value.trim().isEmpty()
                ? null
                : value.trim();
    }
}