package com.account.serviceImpl.ledger;

import com.account.domain.User;
import com.account.domain.ledger.AccountingVoucher;
import com.account.domain.ledger.AccountingVoucherEntry;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroup;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherEntryResponseDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.dto.operationService.*;
import com.account.dto.operationService.GovernmentFeeFundTransferPostingRequestDto;
import com.account.dto.operationService.GovernmentFeeFundTransferPostingResponseDto;
import com.account.dto.operationService.GovernmentFeePaymentPostingRequestDto;
import com.account.dto.operationService.GovernmentFeePaymentPostingResponseDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectExpenseAccountingServiceImpl
        implements ProjectExpenseAccountingService {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;


    private static final String GOVERNMENT_FEE_CLIENT_ADVANCE_CODE =
            "LED-GOV-FEE-ADV";

    private static final String GOVERNMENT_FEE_PAYABLE_CODE =
            "LED-GOV-FEE-PAY";

    private static final String GOVERNMENT_FEE_RECEIVABLE_CODE =
            "LED-GOV-FEE-REC";

    private static final String MAIN_CASH_LEDGER_CODE =
            "LED-CASH-MAIN";

    private static final Set<VoucherSourceType> GOVERNMENT_FEE_VOUCHER_SOURCE_TYPES =
            EnumSet.of(
                    VoucherSourceType.PROJECT_EXPENSE_CLIENT_RECEIPT,
                    VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                    VoucherSourceType.PROJECT_EXPENSE_FUND_TRANSFER,
                    VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_PAYMENT
            );

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

    private static final Set<String> GOVERNMENT_PAYMENT_MODES =
            Set.of(
                    "NET_BANKING",
                    "NEFT",
                    "RTGS",
                    "IMPS",
                    "UPI",
                    "CARD",
                    "BANK_TRANSFER",
                    "CHEQUE",
                    "DEMAND_DRAFT",
                    "OTHER"
            );

    private final AccountingVoucherRepository accountingVoucherRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;
    private final UserRepository userRepository;
    private final AccountingVoucherService accountingVoucherService;

    /**
     * Both vouchers for CLIENT_TO_COMPANY are created inside this transaction.
     * If creation of either voucher fails, both voucher and ledger-balance
     * changes are rolled back by Spring.
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public GovernmentFeePostingResponseDto postGovernmentFeeExpense(
            GovernmentFeePostingRequestDto request
    ) {

        log.info(
                "[ACC-STEP3-REQUEST-RECEIVED] requestNull={} | operationExpenseId={} | projectNo={} | " +
                        "paidBy={} | amount={} | paymentMode={} | receivingBankLedgerId={} | " +
                        "receivingBankName={} | clientLedgerId={} | paymentDate={} | referencePresent={} | proofPresent={} | " +
                        "clientCompanyId={} | clientUnitId={}",
                request == null,
                request != null ? request.getOperationExpenseId() : null,
                request != null ? request.getProjectNo() : null,
                request != null ? request.getPaidBy() : null,
                request != null ? request.getApprovedAmount() : null,
                request != null ? request.getClientPaymentMode() : null,
                request != null ? request.getClientPaymentBankLedgerId() : null,
                request != null ? request.getClientPaymentBankName() : null,
                request != null ? request.getClientLedgerId() : null,
                request != null ? request.getClientPaymentDate() : null,
                request != null && hasText(request.getClientPaymentReference()),
                request != null && hasText(request.getClientPaymentProofUrl()),
                request != null ? request.getClientCompanyId() : null,
                request != null ? request.getClientUnitId() : null
        );

        validateRequest(request);

        log.info(
                "[ACC-STEP3-BASE-VALIDATION-SUCCESS] operationExpenseId={} | paidBy={} | amount={}",
                request.getOperationExpenseId(),
                request.getPaidBy(),
                request.getApprovedAmount()
        );

        log.info(
                "[GOVERNMENT-FEE-POSTING-START] operationExpenseId={} | projectNo={} | paidBy={} | amount={}",
                request.getOperationExpenseId(),
                request.getProjectNo(),
                request.getPaidBy(),
                request.getApprovedAmount()
        );

        log.info(
                "[ACC-STEP3-BRANCH-SELECT] operationExpenseId={} | branch={}",
                request.getOperationExpenseId(),
                request.getPaidBy()
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

    // =========================================================
    // STEP 4 - INTER-BANK CONTRA
    // =========================================================

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public GovernmentFeeFundTransferPostingResponseDto
    postGovernmentFeeFundTransfer(
            GovernmentFeeFundTransferPostingRequestDto request
    ) {
        log.info(
                "[ACC-STEP4-REQUEST-RECEIVED] requestNull={} | operationExpenseId={} | fromBankLedgerId={} | " +
                        "toBankLedgerId={} | amount={} | transferDate={} | referencePresent={} | proofPresent={}",
                request == null,
                request != null ? request.getOperationExpenseId() : null,
                request != null ? request.getFromBankLedgerId() : null,
                request != null ? request.getToBankLedgerId() : null,
                request != null ? request.getAmount() : null,
                request != null ? request.getTransferDate() : null,
                request != null && hasText(request.getTransferReference()),
                request != null && hasText(request.getTransferProofUrl())
        );

        validateFundTransferRequest(request);

        log.info(
                "[ACC-STEP4-VALIDATION-SUCCESS] operationExpenseId={} | fromBankLedgerId={} | toBankLedgerId={}",
                request.getOperationExpenseId(),
                request.getFromBankLedgerId(),
                request.getToBankLedgerId()
        );

        Optional<AccountingVoucher> existing = findPostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_FUND_TRANSFER,
                request.getOperationExpenseId()
        );

        if (existing.isPresent()) {
            AccountingVoucher voucher = existing.get();
            LedgerMaster fromBank = resolveVoucherBankLedger(
                    voucher,
                    DebitCredit.CREDIT,
                    "Existing CONTRA voucher does not contain a source-bank credit entry"
            );
            LedgerMaster toBank = resolveVoucherBankLedger(
                    voucher,
                    DebitCredit.DEBIT,
                    "Existing CONTRA voucher does not contain a destination-bank debit entry"
            );

            if (!Objects.equals(
                    fromBank.getId(),
                    request.getFromBankLedgerId()
            ) || !Objects.equals(
                    toBank.getId(),
                    request.getToBankLedgerId()
            ) || money(voucher.getTotalDebit()).compareTo(
                    money(request.getAmount())
            ) != 0) {
                throw new ValidationException(
                        "A fund-transfer voucher already exists with different bank or amount details",
                        "ERR_FUND_TRANSFER_IDEMPOTENCY_CONFLICT",
                        "operationExpenseId"
                );
            }

            return GovernmentFeeFundTransferPostingResponseDto.builder()
                    .postingStatus("ALREADY_POSTED")
                    .message("Government-fee fund transfer was already posted")
                    .operationExpenseId(request.getOperationExpenseId())
                    .contraVoucherId(voucher.getId())
                    .contraVoucherNumber(voucher.getVoucherNumber())
                    .fromBankLedgerId(fromBank.getId())
                    .toBankLedgerId(toBank.getId())
                    .postedAt(resolvePostedAt(voucher))
                    .build();
        }

        AccountingVoucher accrualVoucher = requirePostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                request.getOperationExpenseId(),
                "Complete Step 3 government-fee accrual before fund transfer"
        );

        BigDecimal amount = money(request.getAmount());

        if (amount.compareTo(money(accrualVoucher.getTotalDebit())) != 0) {
            throw new ValidationException(
                    "Fund-transfer amount must equal the approved government-fee amount",
                    "ERR_FUND_TRANSFER_AMOUNT_MISMATCH",
                    "amount"
            );
        }

        Optional<AccountingVoucher> receiptVoucher = findPostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_CLIENT_RECEIPT,
                request.getOperationExpenseId()
        );

        if (receiptVoucher.isPresent()) {
            LedgerMaster receiptBank = resolveVoucherBankLedger(
                    receiptVoucher.get(),
                    DebitCredit.DEBIT,
                    "Client receipt voucher does not contain a receiving-bank debit"
            );

            if (!receiptBank.getId().equals(request.getFromBankLedgerId())) {
                throw new ValidationException(
                        "Fund-transfer source bank must match the client receipt bank",
                        "ERR_FUND_TRANSFER_SOURCE_BANK_MISMATCH",
                        "fromBankLedgerId"
                );
            }
        }

        LedgerMaster fromBank = resolveActiveBankLedger(
                request.getFromBankLedgerId(),
                "fromBankLedgerId"
        );
        LedgerMaster toBank = resolveActiveBankLedger(
                request.getToBankLedgerId(),
                "toBankLedgerId"
        );

        log.info(
                "[ACC-STEP4-BANKS-RESOLVED] operationExpenseId={} | fromLedgerId={} | fromLedgerName={} | " +
                        "toLedgerId={} | toLedgerName={} | amount={}",
                request.getOperationExpenseId(),
                fromBank.getId(),
                fromBank.getLedgerName(),
                toBank.getId(),
                toBank.getLedgerName(),
                amount
        );

        log.info(
                "[ACC-STEP4-VOUCHER-ENTRIES] operationExpenseId={} | debitLedgerId={} | debitAmount={} | " +
                        "creditLedgerId={} | creditAmount={}",
                request.getOperationExpenseId(),
                toBank.getId(),
                amount,
                fromBank.getId(),
                amount
        );

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.CONTRA)
                        .voucherDate(request.getTransferDate())
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_FUND_TRANSFER
                        )
                        .sourceId(request.getOperationExpenseId())
                        .projectId(accrualVoucher.getProjectId())
                        .projectNo(accrualVoucher.getProjectNo())
                        .projectName(accrualVoucher.getProjectName())
                        .clientCompanyId(accrualVoucher.getClientCompanyId())
                        .clientCompanyName(accrualVoucher.getClientCompanyName())
                        .clientUnitId(accrualVoucher.getClientUnitId())
                        .clientUnitName(accrualVoucher.getClientUnitName())
                        .expensePaidBy(accrualVoucher.getExpensePaidBy())
                        .narration(firstNonBlank(
                                request.getNarration(),
                                "Government-fee bank transfer for project "
                                        + safeProjectNumber(
                                        request.getProjectNo(),
                                        request.getProjectId())
                                        + ", reference "
                                        + clean(request.getTransferReference())
                        ))
                        .entries(List.of(
                                debitEntry(
                                        toBank.getId(),
                                        amount,
                                        "Government-fee funds received in payment bank"
                                ),
                                creditEntry(
                                        fromBank.getId(),
                                        amount,
                                        "Government-fee funds transferred from source bank"
                                )
                        ))
                        .build();

        AccountingVoucherResponseDto created =
                accountingVoucherService.createVoucher(voucherRequest);
        AccountingVoucher voucher = getCreatedVoucher(created.getId());

        log.info(
                "[ACC-STEP4-POSTED] operationExpenseId={} | voucherId={} | voucherNumber={} | " +
                        "fromBankLedgerId={} | toBankLedgerId={} | amount={}",
                request.getOperationExpenseId(),
                voucher.getId(),
                voucher.getVoucherNumber(),
                fromBank.getId(),
                toBank.getId(),
                amount
        );

        return GovernmentFeeFundTransferPostingResponseDto.builder()
                .postingStatus("POSTED")
                .message("Government-fee fund transfer posted successfully")
                .operationExpenseId(request.getOperationExpenseId())
                .contraVoucherId(voucher.getId())
                .contraVoucherNumber(voucher.getVoucherNumber())
                .fromBankLedgerId(fromBank.getId())
                .toBankLedgerId(toBank.getId())
                .postedAt(resolvePostedAt(voucher))
                .build();
    }

    // =========================================================
    // STEP 5 - PAYMENT TO GOVERNMENT
    // =========================================================

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public GovernmentFeePaymentPostingResponseDto postGovernmentFeePayment(
            GovernmentFeePaymentPostingRequestDto request
    ) {
        log.info(
                "[ACC-STEP5-REQUEST-RECEIVED] requestNull={} | operationExpenseId={} | paidBy={} | " +
                        "paymentBankLedgerId={} | amount={} | paymentDate={} | paymentMode={} | " +
                        "referencePresent={} | receiptPresent={}",
                request == null,
                request != null ? request.getOperationExpenseId() : null,
                request != null ? request.getPaidBy() : null,
                request != null ? request.getPaymentBankLedgerId() : null,
                request != null ? request.getAmount() : null,
                request != null ? request.getPaymentDate() : null,
                request != null ? request.getPaymentMode() : null,
                request != null && hasText(request.getPaymentReference()),
                request != null && hasText(request.getPaymentReceiptUrl())
        );

        validateGovernmentPaymentRequest(request);

        log.info(
                "[ACC-STEP5-VALIDATION-SUCCESS] operationExpenseId={} | paymentBankLedgerId={} | amount={}",
                request.getOperationExpenseId(),
                request.getPaymentBankLedgerId(),
                request.getAmount()
        );

        Optional<AccountingVoucher> existing = findPostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_PAYMENT,
                request.getOperationExpenseId()
        );

        if (existing.isPresent()) {
            AccountingVoucher voucher = existing.get();
            LedgerMaster paymentBank = resolveVoucherBankLedger(
                    voucher,
                    DebitCredit.CREDIT,
                    "Existing PAYMENT voucher does not contain a payment-bank credit entry"
            );
            LedgerMaster payableLedger = resolveVoucherLedgerByType(
                    voucher,
                    LedgerType.GOVERNMENT_FEE_PAYABLE,
                    DebitCredit.DEBIT,
                    "Existing PAYMENT voucher does not contain Government Fee Payable debit entry"
            );

            if (!Objects.equals(
                    paymentBank.getId(),
                    request.getPaymentBankLedgerId()
            ) || money(voucher.getTotalDebit()).compareTo(
                    money(request.getAmount())
            ) != 0) {
                throw new ValidationException(
                        "A government-payment voucher already exists with different bank or amount details",
                        "ERR_GOVERNMENT_PAYMENT_IDEMPOTENCY_CONFLICT",
                        "operationExpenseId"
                );
            }

            return GovernmentFeePaymentPostingResponseDto.builder()
                    .postingStatus("ALREADY_POSTED")
                    .message("Government-fee payment was already posted")
                    .operationExpenseId(request.getOperationExpenseId())
                    .paymentVoucherId(voucher.getId())
                    .paymentVoucherNumber(voucher.getVoucherNumber())
                    .paymentBankLedgerId(paymentBank.getId())
                    .governmentFeePayableLedgerId(payableLedger.getId())
                    .postedAt(resolvePostedAt(voucher))
                    .build();
        }

        AccountingVoucher accrualVoucher = requirePostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                request.getOperationExpenseId(),
                "Complete Step 3 government-fee accrual before payment"
        );

        AccountingVoucher transferVoucher = requirePostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_FUND_TRANSFER,
                request.getOperationExpenseId(),
                "Complete Step 4 fund transfer before payment"
        );

        BigDecimal amount = money(request.getAmount());

        if (amount.compareTo(money(accrualVoucher.getTotalDebit())) != 0
                || amount.compareTo(money(transferVoucher.getTotalDebit())) != 0) {
            throw new ValidationException(
                    "Government payment amount must equal the accrual and transfer amounts",
                    "ERR_GOVERNMENT_PAYMENT_AMOUNT_MISMATCH",
                    "amount"
            );
        }

        LedgerMaster transferDestinationBank = resolveVoucherBankLedger(
                transferVoucher,
                DebitCredit.DEBIT,
                "Fund-transfer voucher does not contain a destination-bank debit"
        );

        if (!transferDestinationBank.getId()
                .equals(request.getPaymentBankLedgerId())) {
            throw new ValidationException(
                    "Payment bank must match the Step 4 destination bank",
                    "ERR_PAYMENT_BANK_MISMATCH",
                    "paymentBankLedgerId"
            );
        }

        LedgerMaster paymentBank = resolveActiveBankLedger(
                request.getPaymentBankLedgerId(),
                "paymentBankLedgerId"
        );

        /*
         * The payable must already exist because Step 3 created it. We do not
         * create a new payable during payment; doing so could hide an invalid
         * workflow or allow payment without the approval accrual.
         */
        LedgerMaster payableLedger = resolveGovernmentFeePayableLedger();

        log.info(
                "[ACC-STEP5-LEDGERS-RESOLVED] operationExpenseId={} | debitPayableLedgerId={} | " +
                        "debitPayableLedgerName={} | creditBankLedgerId={} | creditBankLedgerName={} | amount={}",
                request.getOperationExpenseId(),
                payableLedger.getId(),
                payableLedger.getLedgerName(),
                paymentBank.getId(),
                paymentBank.getLedgerName(),
                amount
        );
        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.PAYMENT)
                        .voucherDate(request.getPaymentDate())
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_PAYMENT
                        )
                        .sourceId(request.getOperationExpenseId())
                        .projectId(accrualVoucher.getProjectId())
                        .projectNo(accrualVoucher.getProjectNo())
                        .projectName(accrualVoucher.getProjectName())
                        .clientCompanyId(accrualVoucher.getClientCompanyId())
                        .clientCompanyName(accrualVoucher.getClientCompanyName())
                        .clientUnitId(accrualVoucher.getClientUnitId())
                        .clientUnitName(accrualVoucher.getClientUnitName())
                        .expensePaidBy(accrualVoucher.getExpensePaidBy())
                        .narration(firstNonBlank(
                                request.getNarration(),
                                "Government fee paid for project "
                                        + safeProjectNumber(
                                        request.getProjectNo(),
                                        request.getProjectId())
                                        + ", reference "
                                        + clean(request.getPaymentReference())
                        ))
                        .entries(List.of(
                                debitEntry(
                                        payableLedger.getId(),
                                        amount,
                                        "Government-fee payable settled"
                                ),
                                creditEntry(
                                        paymentBank.getId(),
                                        amount,
                                        "Government fee paid from bank"
                                )
                        ))
                        .build();

        AccountingVoucherResponseDto created =
                accountingVoucherService.createVoucher(voucherRequest);
        AccountingVoucher voucher = getCreatedVoucher(created.getId());

        log.info(
                "[GOVERNMENT-FEE-PAYMENT-POSTED] operationExpenseId={} | paymentVoucherId={} | paymentVoucherNumber={} | payableLedgerId={} | bankLedgerId={} | amount={}",
                request.getOperationExpenseId(),
                voucher.getId(),
                voucher.getVoucherNumber(),
                payableLedger.getId(),
                paymentBank.getId(),
                amount
        );

        return GovernmentFeePaymentPostingResponseDto.builder()
                .postingStatus("POSTED")
                .message("Government-fee payment posted successfully")
                .operationExpenseId(request.getOperationExpenseId())
                .paymentVoucherId(voucher.getId())
                .paymentVoucherNumber(voucher.getVoucherNumber())
                .governmentFeePayableLedgerId(payableLedger.getId())
                .paymentBankLedgerId(paymentBank.getId())
                .postedAt(resolvePostedAt(voucher))
                .build();
    }

    private GovernmentFeePostingResponseDto postClientFundedGovernmentFee(
            GovernmentFeePostingRequestDto request
    ) {

        log.info(
                "[ACC-CLIENT-FUNDED-START] operationExpenseId={} | paymentMode={} | " +
                        "receivingBankLedgerId={} | receivingBankName={} | clientLedgerId={} | amount={}",
                request.getOperationExpenseId(),
                request.getClientPaymentMode(),
                request.getClientPaymentBankLedgerId(),
                request.getClientPaymentBankName(),
                request.getClientLedgerId(),
                request.getApprovedAmount()
        );

        validateClientFundingDetails(request);

        log.info(
                "[ACC-CLIENT-FUNDED-VALIDATION-SUCCESS] operationExpenseId={} | receivingBankLedgerId={}",
                request.getOperationExpenseId(),
                request.getClientPaymentBankLedgerId()
        );

        /*
         * =========================================================
         * NEW CLIENT_TO_COMPANY REQUIREMENT
         * =========================================================
         *
         * Keep all existing flows unchanged except the CLIENT_TO_COMPANY
         * accrual representation.
         *
         * STEP 3A - Receipt:
         *      Dr Bank
         *      Cr Customer
         *
         * STEP 3B - Customer Debit Note / Government-fee accrual:
         *      Dr Customer
         *      Cr Government Fee Receivable
         *
         * IMPORTANT:
         *      - Government Fee Receivable gets CREDIT only from this flow.
         *      - Government Fee Payable is NOT touched in Step 3 for this flow.
         *      - The same existing PROJECT_EXPENSE_GOVT_FEE_ACCRUAL source type
         *        is reused so Step 4 and Step 5 continue to work unchanged.
         *      - No new VoucherSourceType is introduced.
         *
         * STEP 5 remains unchanged:
         *      Dr Government Fee Payable
         *      Cr Payment Bank
         * =========================================================
         */

        Optional<AccountingVoucher> existingReceipt =
                findPostedVoucher(
                        VoucherSourceType.PROJECT_EXPENSE_CLIENT_RECEIPT,
                        request.getOperationExpenseId()
                );

        Optional<AccountingVoucher> existingAccrual =
                findPostedVoucher(
                        VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                        request.getOperationExpenseId()
                );

        log.info(
                "[ACC-CLIENT-FUNDED-IDEMPOTENCY-CHECK] operationExpenseId={} | " +
                        "existingReceiptPresent={} | existingReceiptId={} | " +
                        "existingAccrualPresent={} | existingAccrualId={}",
                request.getOperationExpenseId(),
                existingReceipt.isPresent(),
                existingReceipt.map(AccountingVoucher::getId).orElse(null),
                existingAccrual.isPresent(),
                existingAccrual.map(AccountingVoucher::getId).orElse(null)
        );

        validateExistingVoucherAmount(
                existingReceipt,
                request.getApprovedAmount(),
                "Existing client receipt amount differs from the approved amount"
        );

        validateExistingVoucherAmount(
                existingAccrual,
                request.getApprovedAmount(),
                "Existing government-fee accrual amount differs from the approved amount"
        );

        User approver =
                resolveApprover(
                        request.getApprovedByUserId()
                );

        /*
         * Reuse the receiving ledger from an already-posted receipt when
         * possible. Otherwise resolve it from the request exactly as before.
         */
        LedgerMaster receivingLedger =
                existingReceipt
                        .map(voucher -> resolveVoucherLedgerBySide(
                                voucher,
                                DebitCredit.DEBIT,
                                "Existing client receipt voucher does not contain a debit receiving ledger"
                        ))
                        .orElseGet(() -> resolveReceivingLedger(request, approver));

        log.info(
                "[ACC-RECEIVING-LEDGER-RESOLVED] operationExpenseId={} | ledgerId={} | ledgerCode={} | " +
                        "ledgerName={} | ledgerType={} | active={}",
                request.getOperationExpenseId(),
                receivingLedger.getId(),
                receivingLedger.getLedgerCode(),
                receivingLedger.getLedgerName(),
                receivingLedger.getLedgerType(),
                receivingLedger.isActive()
        );

        /*
         * Reuse the customer ledger from an existing receipt. If no receipt
         * exists yet, use the existing customer-ledger resolution unchanged.
         */
        LedgerMaster customerLedger =
                existingReceipt
                        .map(voucher -> resolveVoucherLedgerByType(
                                voucher,
                                LedgerType.CUSTOMER,
                                DebitCredit.CREDIT,
                                "Existing client receipt voucher does not contain a CUSTOMER credit entry. " +
                                        "Cancel/reverse the old receipt voucher and repost the government-fee approval."
                        ))
                        .orElseGet(() -> resolveCompanyFundedCustomerLedger(request, approver));

        log.info(
                "[ACC-CUSTOMER-LEDGER-RESOLVED] operationExpenseId={} | ledgerId={} | ledgerCode={} | ledgerName={}",
                request.getOperationExpenseId(),
                customerLedger.getId(),
                customerLedger.getLedgerCode(),
                customerLedger.getLedgerName()
        );

        /*
         * Keep the system payable ledger available for Step 5, but DO NOT
         * post a Step-3 credit to it for CLIENT_TO_COMPANY.
         */
        LedgerMaster payableLedger =
                getOrCreateSystemLedger(
                        LedgerType.GOVERNMENT_FEE_PAYABLE,
                        LedgerGroupType.CURRENT_LIABILITIES,
                        "Government Fee Payable",
                        GOVERNMENT_FEE_PAYABLE_CODE,
                        DebitCredit.CREDIT,
                        approver
                );

        log.info(
                "[ACC-PAYABLE-LEDGER-RESOLVED-NO-STEP3-POSTING] operationExpenseId={} | " +
                        "ledgerId={} | ledgerCode={} | ledgerName={}",
                request.getOperationExpenseId(),
                payableLedger.getId(),
                payableLedger.getLedgerCode(),
                payableLedger.getLedgerName()
        );

        LedgerMaster receivableLedger =
                getOrCreateSystemLedger(
                        LedgerType.GOVERNMENT_FEE_RECEIVABLE,
                        LedgerGroupType.CURRENT_ASSETS,
                        "Government Fee Receivable",
                        GOVERNMENT_FEE_RECEIVABLE_CODE,
                        DebitCredit.DEBIT,
                        approver
                );

        log.info(
                "[ACC-RECEIVABLE-LEDGER-RESOLVED] operationExpenseId={} | ledgerId={} | ledgerCode={} | ledgerName={}",
                request.getOperationExpenseId(),
                receivableLedger.getId(),
                receivableLedger.getLedgerCode(),
                receivableLedger.getLedgerName()
        );

        BigDecimal amount =
                money(
                        request.getApprovedAmount()
                );

        /*
         * ENTRY A - UNCHANGED
         *
         * Dr Bank
         * Cr Customer
         */
        AccountingVoucher receiptVoucher =
                existingReceipt.orElseGet(() ->
                        createClientReceiptVoucher(
                                request,
                                receivingLedger,
                                customerLedger,
                                amount
                        )
                );

        log.info(
                "[ACC-CLIENT-RECEIPT-READY] operationExpenseId={} | voucherId={} | voucherNumber={} | " +
                        "debitBankLedgerId={} | creditCustomerLedgerId={} | amount={}",
                request.getOperationExpenseId(),
                receiptVoucher.getId(),
                receiptVoucher.getVoucherNumber(),
                receivingLedger.getId(),
                customerLedger.getId(),
                amount
        );

        /*
         * ENTRY B - NEW REQUIRED SHAPE
         *
         * Dr Customer
         * Cr Government Fee Receivable
         *
         * This voucher itself is the Step-3 accrual marker by using the
         * EXISTING PROJECT_EXPENSE_GOVT_FEE_ACCRUAL source type.
         * Therefore no extra Dr Receivable / Cr Payable journal is created.
         */
        AccountingVoucher accrualVoucher;

        if (existingAccrual.isPresent()) {
            AccountingVoucher existingVoucher = existingAccrual.get();

            boolean newRequiredShape =
                    hasVoucherEntry(
                            existingVoucher,
                            LedgerType.CUSTOMER,
                            DebitCredit.DEBIT
                    )
                            && hasVoucherEntry(
                            existingVoucher,
                            LedgerType.GOVERNMENT_FEE_RECEIVABLE,
                            DebitCredit.CREDIT
                    );

            boolean legacyShape =
                    hasVoucherEntry(
                            existingVoucher,
                            LedgerType.GOVERNMENT_FEE_RECEIVABLE,
                            DebitCredit.DEBIT
                    )
                            && hasVoucherEntry(
                            existingVoucher,
                            LedgerType.GOVERNMENT_FEE_PAYABLE,
                            DebitCredit.CREDIT
                    );

            if (newRequiredShape) {
                accrualVoucher = existingVoucher;

                log.info(
                        "[ACC-CLIENT-ACCRUAL-ALREADY-POSTED-NEW-SHAPE] operationExpenseId={} | " +
                                "voucherId={} | voucherNumber={} | DR_CUSTOMER={} | CR_RECEIVABLE={} | amount={}",
                        request.getOperationExpenseId(),
                        accrualVoucher.getId(),
                        accrualVoucher.getVoucherNumber(),
                        customerLedger.getId(),
                        receivableLedger.getId(),
                        amount
                );
            } else if (legacyShape) {
                /*
                 * Do not mutate/repost old accounting. Existing historical
                 * vouchers remain exactly as they were posted.
                 */
                LedgerMaster legacyPayableLedger = resolveVoucherLedgerByType(
                        existingVoucher,
                        LedgerType.GOVERNMENT_FEE_PAYABLE,
                        DebitCredit.CREDIT,
                        "Existing legacy accrual voucher does not contain Government Fee Payable credit entry"
                );

                log.warn(
                        "[ACC-CLIENT-ACCRUAL-LEGACY-SHAPE-PRESERVED] operationExpenseId={} | " +
                                "voucherId={} | voucherNumber={} | " +
                                "reason=historical-voucher-not-mutated",
                        request.getOperationExpenseId(),
                        existingVoucher.getId(),
                        existingVoucher.getVoucherNumber()
                );

                return buildClientFundedResponse(
                        request,
                        "ALREADY_POSTED",
                        "Existing historical client government-fee vouchers were preserved without modification",
                        receiptVoucher,
                        existingVoucher,
                        receivingLedger,
                        customerLedger,
                        legacyPayableLedger
                );
            } else {
                throw new ValidationException(
                        "Existing government-fee accrual voucher has an unsupported ledger-entry structure",
                        "ERR_GOVERNMENT_FEE_ACCRUAL_STRUCTURE_MISMATCH",
                        "operationExpenseId"
                );
            }
        } else {
            accrualVoucher =
                    createClientGovernmentFeeCustomerDebitNote(
                            request,
                            customerLedger,
                            receivableLedger,
                            amount
                    );
        }

        log.info(
                "[ACC-CLIENT-ACCRUAL-READY-NEW-REQUIREMENT] operationExpenseId={} | " +
                        "voucherId={} | voucherNumber={} | " +
                        "debitCustomerLedgerId={} | creditReceivableLedgerId={} | " +
                        "payableStep3Posting=false | amount={}",
                request.getOperationExpenseId(),
                accrualVoucher.getId(),
                accrualVoucher.getVoucherNumber(),
                customerLedger.getId(),
                receivableLedger.getId(),
                amount
        );

        log.info(
                "[CLIENT-FUNDED-GOVERNMENT-FEE-POSTED] " +
                        "operationExpenseId={} | " +
                        "receiptVoucherId={} | accrualVoucherId={} | " +
                        "customerLedgerId={} | receivableLedgerId={} | " +
                        "payableLedgerId={} | amount={}",
                request.getOperationExpenseId(),
                receiptVoucher.getId(),
                accrualVoucher.getId(),
                customerLedger.getId(),
                receivableLedger.getId(),
                payableLedger.getId(),
                amount
        );

        return buildClientFundedResponse(
                request,
                "POSTED",
                "Client government-fee receipt and customer debit-note accrual posted successfully",
                receiptVoucher,
                accrualVoucher,
                receivingLedger,
                customerLedger,
                payableLedger
        );
    }

    private GovernmentFeePostingResponseDto postCompanyFundedGovernmentFee(
            GovernmentFeePostingRequestDto request
    ) {

        /*
         * COMPANY-funded government fee:
         *
         * Corpseed pays the government fee on behalf of the client and expects
         * to recover the amount later.
         *
         * ACCOUNTING (DEBIT_NOTE voucher):
         *
         * Dr GOVERNMENT_FEE_RECEIVABLE  (CURRENT_ASSETS)   ← Corpseed advanced money, expects recovery
         * Cr GOVERNMENT_FEE_PAYABLE     (CURRENT_LIABILITIES) ← liability to the government is created
         *
         * The GOVERNMENT_FEE_RECEIVABLE ledger tracks all amounts Corpseed has
         * advanced on behalf of clients across all projects in one place.
         * The client's CUSTOMER ledger is NOT debited here because Corpseed
         * is bearing the cost — client recovery happens through invoicing, not
         * directly through this accounting entry.
         */

        validateCompanyFundedClientDetails(request);

        Optional<AccountingVoucher> existingJournal =
                findPostedVoucher(
                        VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                        request.getOperationExpenseId()
                );

        if (existingJournal.isPresent()) {
            AccountingVoucher journal = existingJournal.get();

            validateExistingVoucherAmount(
                    existingJournal,
                    request.getApprovedAmount(),
                    "Existing company-funded debit-note amount differs from the approved amount"
            );

            LedgerMaster existingReceivableLedger = resolveVoucherLedgerByType(
                    journal,
                    LedgerType.GOVERNMENT_FEE_RECEIVABLE,
                    DebitCredit.DEBIT,
                    "Existing company-funded government-fee accrual does not contain "
                            + "a GOVERNMENT_FEE_RECEIVABLE debit entry."
            );

            LedgerMaster existingPayableLedger = resolveVoucherLedgerByType(
                    journal,
                    LedgerType.GOVERNMENT_FEE_PAYABLE,
                    DebitCredit.CREDIT,
                    "Existing company-funded government-fee accrual does not contain "
                            + "a GOVERNMENT_FEE_PAYABLE credit entry."
            );

            log.info(
                    "[COMPANY-FUNDED-GOVERNMENT-FEE-ALREADY-POSTED] "
                            + "operationExpenseId={} | journalVoucherId={} | "
                            + "receivableLedgerId={} | receivableLedgerName={} | payableLedgerId={}",
                    request.getOperationExpenseId(),
                    journal.getId(),
                    existingReceivableLedger.getId(),
                    existingReceivableLedger.getLedgerName(),
                    existingPayableLedger.getId()
            );

            return GovernmentFeePostingResponseDto.builder()
                    .postingStatus("ALREADY_POSTED")
                    .message(
                            "Company-funded government-fee receivable was already posted"
                    )
                    .operationExpenseId(request.getOperationExpenseId())
                    .journalVoucherId(journal.getId())
                    .journalVoucherNumber(journal.getVoucherNumber())
                    .clientAdvanceLedgerId(null)
                    .governmentFeePayableLedgerId(existingPayableLedger.getId())
                    .governmentFeeExpenseLedgerId(null)
                    .voucherId(journal.getId())
                    .voucherNumber(journal.getVoucherNumber())
                    .postedAt(resolvePostedAt(journal))
                    .build();
        }

        User approver =
                resolveApprover(
                        request.getApprovedByUserId()
                );

        /*
         * GOVERNMENT_FEE_RECEIVABLE — tracks the total amount Corpseed has
         * advanced to the government on behalf of clients. One shared system
         * ledger for all projects (not per-client).
         */
        LedgerMaster receivableLedger =
                getOrCreateSystemLedger(
                        LedgerType.GOVERNMENT_FEE_RECEIVABLE,
                        LedgerGroupType.CURRENT_ASSETS,
                        "Government Fee Receivable",
                        GOVERNMENT_FEE_RECEIVABLE_CODE,
                        DebitCredit.DEBIT,
                        approver
                );

        LedgerMaster payableLedger =
                getOrCreateSystemLedger(
                        LedgerType.GOVERNMENT_FEE_PAYABLE,
                        LedgerGroupType.CURRENT_LIABILITIES,
                        "Government Fee Payable",
                        GOVERNMENT_FEE_PAYABLE_CODE,
                        DebitCredit.CREDIT,
                        approver
                );

        BigDecimal amount =
                money(
                        request.getApprovedAmount()
                );

        AccountingVoucher journal =
                createCompanyFundedGovernmentFeeReceivableJournal(
                        request,
                        receivableLedger,
                        payableLedger,
                        amount
                );

        log.info(
                "[COMPANY-FUNDED-GOVERNMENT-FEE-POSTED] "
                        + "operationExpenseId={} | "
                        + "journalVoucherId={} | "
                        + "journalVoucherNumber={} | "
                        + "receivableLedgerId={} | "
                        + "receivableLedgerName={} | "
                        + "payableLedgerId={} | "
                        + "clientCompanyId={} | clientUnitId={} | amount={}",
                request.getOperationExpenseId(),
                journal.getId(),
                journal.getVoucherNumber(),
                receivableLedger.getId(),
                receivableLedger.getLedgerName(),
                payableLedger.getId(),
                request.getClientCompanyId(),
                request.getClientUnitId(),
                amount
        );

        return GovernmentFeePostingResponseDto.builder()
                .postingStatus("POSTED")
                .message(
                        "Company-funded government-fee receivable and payable posted successfully"
                )
                .operationExpenseId(request.getOperationExpenseId())
                .journalVoucherId(journal.getId())
                .journalVoucherNumber(journal.getVoucherNumber())
                .clientAdvanceLedgerId(null)
                .governmentFeePayableLedgerId(
                        payableLedger.getId()
                )
                .governmentFeeExpenseLedgerId(null)
                .voucherId(journal.getId())
                .voucherNumber(journal.getVoucherNumber())
                .postedAt(resolvePostedAt(journal))
                .build();
    }

    private AccountingVoucher createClientReceiptVoucher(
            GovernmentFeePostingRequestDto request,
            LedgerMaster receivingLedger,
            LedgerMaster customerLedger,
            BigDecimal amount
    ) {

        log.info(
                "[ACC-CLIENT-RECEIPT-CREATE-START] operationExpenseId={} | voucherType={} | " +
                        "debitLedgerId={} | debitLedgerName={} | creditLedgerId={} | creditLedgerName={} | amount={}",
                request.getOperationExpenseId(),
                VoucherType.RECEIPT,
                receivingLedger.getId(),
                receivingLedger.getLedgerName(),
                customerLedger.getId(),
                customerLedger.getLedgerName(),
                amount
        );

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.RECEIPT)
                        .voucherDate(request.getClientPaymentDate())
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_CLIENT_RECEIPT
                        )
                        .sourceId(request.getOperationExpenseId())
                        .projectId(request.getProjectId())
                        .projectNo(request.getProjectNo())
                        .projectName(request.getProjectName())
                        .clientCompanyId(request.getClientCompanyId())
                        .clientCompanyName(request.getClientCompanyName())
                        .clientUnitId(request.getClientUnitId())
                        .clientUnitName(request.getClientUnitName())
                        .expensePaidBy(request.getPaidBy().name())
                        .partyLedgerId(customerLedger.getId())
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
                                        customerLedger.getId(),
                                        amount,
                                        "Government-fee advance received from "
                                                + customerLedger.getLedgerName()
                                )
                        ))
                        .build();

        AccountingVoucherResponseDto response =
                accountingVoucherService.createVoucher(voucherRequest);

        log.info(
                "[ACC-CLIENT-RECEIPT-CREATE-RESPONSE] operationExpenseId={} | voucherId={} | voucherNumber={}",
                request.getOperationExpenseId(),
                response != null ? response.getId() : null,
                response != null ? response.getVoucherNumber() : null
        );

        return getCreatedVoucher(response.getId());
    }

    private AccountingVoucher createClientAccrualJournal(
            GovernmentFeePostingRequestDto request,
            LedgerMaster receivableLedger,
            LedgerMaster payableLedger,
            BigDecimal amount
    ) {

        String customerName = firstNonBlank(
                request.getClientCompanyName(),
                request.getClientUnitName(),
                "Client-" + request.getClientCompanyId()
        );

        /*
         * EXISTING ACCRUAL - KEEP ORIGINAL ACCOUNTING
         *
         * Dr Government Fee Receivable
         * Cr Government Fee Payable
         *
         * Payable is created ONLY here.
         */

        log.info(
                "[ACC-CLIENT-ACCRUAL-CREATE-START] " +
                        "operationExpenseId={} | voucherType={} | " +
                        "debitReceivableLedgerId={} | debitReceivableLedgerName={} | " +
                        "creditPayableLedgerId={} | creditPayableLedgerName={} | amount={}",
                request.getOperationExpenseId(),
                VoucherType.JOURNAL,
                receivableLedger.getId(),
                receivableLedger.getLedgerName(),
                payableLedger.getId(),
                payableLedger.getLedgerName(),
                amount
        );

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.JOURNAL)
                        .voucherDate(resolvePostingDate(request))

                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL
                        )

                        .sourceId(request.getOperationExpenseId())

                        .projectId(request.getProjectId())
                        .projectNo(request.getProjectNo())
                        .projectName(request.getProjectName())

                        .clientCompanyId(request.getClientCompanyId())
                        .clientCompanyName(request.getClientCompanyName())

                        .clientUnitId(request.getClientUnitId())
                        .clientUnitName(request.getClientUnitName())

                        .expensePaidBy(
                                request.getPaidBy().name()
                        )

                        .narration(
                                buildNarration(request)
                        )

                        .entries(
                                List.of(

                                        /*
                                         * DR GOVERNMENT FEE RECEIVABLE
                                         */
                                        debitEntry(
                                                receivableLedger.getId(),
                                                amount,
                                                "Government fee receivable accrued for "
                                                        + customerName
                                        ),

                                        /*
                                         * CR GOVERNMENT FEE PAYABLE
                                         */
                                        creditEntry(
                                                payableLedger.getId(),
                                                amount,
                                                "Government-fee payable created for "
                                                        + customerName
                                        )
                                )
                        )

                        .build();

        AccountingVoucherResponseDto response =
                accountingVoucherService.createVoucher(
                        voucherRequest
                );

        log.info(
                "[ACC-CLIENT-ACCRUAL-CREATE-RESPONSE] " +
                        "operationExpenseId={} | voucherId={} | voucherNumber={} | " +
                        "DR_RECEIVABLE={} | CR_PAYABLE={} | amount={}",
                request.getOperationExpenseId(),
                response != null ? response.getId() : null,
                response != null ? response.getVoucherNumber() : null,
                receivableLedger.getId(),
                payableLedger.getId(),
                amount
        );

        return getCreatedVoucher(
                response.getId()
        );
    }

    private AccountingVoucher createCompanyFundedGovernmentFeeReceivableJournal(
            GovernmentFeePostingRequestDto request,
            LedgerMaster receivableLedger,
            LedgerMaster payableLedger,
            BigDecimal amount
    ) {

        /*
         * COMPANY-funded government fee accounting:
         *
         * Dr Government Fee Receivable  (CURRENT_ASSETS)
         *     Cr Government Fee Payable (CURRENT_LIABILITIES)
         *
         * Dr side: Corpseed has advanced money on behalf of the client.
         *          GOVERNMENT_FEE_RECEIVABLE is an asset — money Corpseed
         *          expects to recover. Increasing this ledger shows the total
         *          amount outstanding to be recovered across all projects.
         *
         * Cr side: Corpseed now owes ₹X to the government portal.
         *          GOVERNMENT_FEE_PAYABLE is a liability — cleared in Step 5
         *          when Axis Bank pays the government.
         *
         * The client's CUSTOMER ledger is NOT touched here. Client recovery
         * is handled through invoicing (a separate billing workflow), not
         * directly through this expense accounting entry.
         */

        String customerName = firstNonBlank(
                request.getClientCompanyName(),
                request.getClientUnitName(),
                "Client-" + request.getClientCompanyId()
        );

        log.info(
                "[ACC-COMPANY-FUNDED-JOURNAL-CREATE-START] operationExpenseId={} | voucherType={} | "
                        + "debitLedgerId={} | debitLedgerName={} | creditLedgerId={} | creditLedgerName={} | amount={}",
                request.getOperationExpenseId(),
                VoucherType.DEBIT_NOTE,
                receivableLedger.getId(),
                receivableLedger.getLedgerName(),
                payableLedger.getId(),
                payableLedger.getLedgerName(),
                amount
        );

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.DEBIT_NOTE)
                        .voucherDate(resolvePostingDate(request))
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL
                        )
                        .sourceId(request.getOperationExpenseId())
                        .projectId(request.getProjectId())
                        .projectNo(request.getProjectNo())
                        .projectName(request.getProjectName())
                        .clientCompanyId(request.getClientCompanyId())
                        .clientCompanyName(request.getClientCompanyName())
                        .clientUnitId(request.getClientUnitId())
                        .clientUnitName(request.getClientUnitName())
                        .expensePaidBy(request.getPaidBy().name())
                        .partyLedgerId(receivableLedger.getId())
                        .narration(
                                "Government fee funded by company for "
                                        + customerName
                                        + " | project "
                                        + safeProjectNumber(request)
                        )
                        .entries(
                                List.of(
                                        /*
                                         * DR GOVERNMENT FEE RECEIVABLE (CURRENT_ASSETS)
                                         *
                                         * Corpseed has advanced money on behalf of the client.
                                         * This is an asset — amount Corpseed expects to recover.
                                         */
                                        debitEntry(
                                                receivableLedger.getId(),
                                                amount,
                                                "Government fee advanced by company for "
                                                        + customerName
                                                        + " | project "
                                                        + safeProjectNumber(request)
                                        ),

                                        /*
                                         * CR GOVERNMENT FEE PAYABLE (CURRENT_LIABILITIES)
                                         *
                                         * Corpseed now owes this amount to the government.
                                         * Cleared in Step 5 when the bank pays the government.
                                         */
                                        creditEntry(
                                                payableLedger.getId(),
                                                amount,
                                                "Government-fee payable created for "
                                                        + customerName
                                                        + " | project "
                                                        + safeProjectNumber(request)
                                        )
                                )
                        )
                        .build();

        AccountingVoucherResponseDto response =
                accountingVoucherService.createVoucher(voucherRequest);

        log.info(
                "[ACC-COMPANY-FUNDED-JOURNAL-CREATE-RESPONSE] operationExpenseId={} | voucherId={} | voucherNumber={}",
                request.getOperationExpenseId(),
                response != null ? response.getId() : null,
                response != null ? response.getVoucherNumber() : null
        );

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

    private AccountingVoucher requirePostedVoucher(
            VoucherSourceType sourceType,
            Long operationExpenseId,
            String message
    ) {
        return findPostedVoucher(sourceType, operationExpenseId)
                .orElseThrow(() -> new ValidationException(
                        message,
                        "ERR_REQUIRED_VOUCHER_NOT_POSTED",
                        "operationExpenseId"
                ));
    }

    private void validateExistingVoucherAmount(
            Optional<AccountingVoucher> existingVoucher,
            BigDecimal requestedAmount,
            String message
    ) {
        if (existingVoucher.isEmpty()) {
            return;
        }

        BigDecimal existingAmount = money(
                existingVoucher.get().getTotalDebit()
        );
        BigDecimal expectedAmount = money(requestedAmount);

        if (existingAmount.compareTo(expectedAmount) != 0) {
            throw new ValidationException(
                    message,
                    "ERR_GOVERNMENT_FEE_IDEMPOTENCY_CONFLICT",
                    "approvedAmount"
            );
        }
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

        log.info(
                "[ACC-RECEIVING-LEDGER-LOOKUP-START] operationExpenseId={} | paymentMode={} | requestedLedgerId={}",
                request.getOperationExpenseId(),
                paymentMode,
                request.getClientPaymentBankLedgerId()
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
            log.warn(
                    "[ACC-RECEIVING-LEDGER-LOOKUP-FAILED] operationExpenseId={} | paymentMode={} | ledgerId={} | reason=missing-or-invalid",
                    request.getOperationExpenseId(),
                    paymentMode,
                    ledgerId
            );
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

        log.info(
                "[ACC-RECEIVING-LEDGER-FOUND] operationExpenseId={} | ledgerId={} | ledgerCode={} | " +
                        "ledgerName={} | ledgerType={} | active={} | deleted={}",
                request.getOperationExpenseId(),
                ledger.getId(),
                ledger.getLedgerCode(),
                ledger.getLedgerName(),
                ledger.getLedgerType(),
                ledger.isActive(),
                ledger.isDeleted()
        );

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

        log.info(
                "[ACC-RECEIVING-LEDGER-VALIDATION-SUCCESS] operationExpenseId={} | ledgerId={} | ledgerType={} | allowedTypes={}",
                request.getOperationExpenseId(),
                ledger.getId(),
                ledger.getLedgerType(),
                allowedTypes
        );

        return ledger;
    }

    /**
     * Resolves the CUSTOMER ledger for this government-fee expense.
     *
     * ================================================================
     * PRIORITY 1: request.getClientLedgerId()
     *
     * If the caller (CRT via Operation Service) explicitly selected a
     * specific client ledger (e.g. "Microsoft"), that exact LedgerMaster
     * row is used, after validating it is:
     *   - an existing, non-deleted ledger
     *   - of type CUSTOMER
     *   - active
     *   - in the SUNDRY_DEBTORS group
     *   - actually owned by the request's clientCompanyId (guards against
     *     accidentally posting against the wrong client's ledger)
     *
     * PRIORITY 2: resolve by clientCompanyId only (matches the ledger
     * resolution used by PaymentServiceImpl/InvoiceServiceImpl for normal
     * Sales Invoice / Payment Receipt flows, so all flows converge on the
     * SAME ledger per company regardless of which unit raised the expense).
     *
     * PRIORITY 3: no CUSTOMER ledger exists anywhere for this company yet
     * (e.g. this is the client's very first transaction). Auto-create one,
     * exactly like the invoice/payment flows do, instead of failing.
     *
     * Used by BOTH funding branches (COMPANY and CLIENT_TO_COMPANY), since
     * both call this same method.
     * ================================================================
     */
    private LedgerMaster resolveCompanyFundedCustomerLedger(
            GovernmentFeePostingRequestDto request,
            User approver
    ) {
        // =========================================================
        // PRIORITY 1 - EXPLICIT CRT OVERRIDE (unchanged)
        // =========================================================
        if (request.getClientLedgerId() != null && request.getClientLedgerId() > 0) {

            log.info(
                    "[CLIENT-LEDGER-EXPLICIT-OVERRIDE-LOOKUP] operationExpenseId={} | clientLedgerId={}",
                    request.getOperationExpenseId(),
                    request.getClientLedgerId()
            );

            LedgerMaster ledger = ledgerMasterRepository
                    .findByIdAndDeletedFalse(request.getClientLedgerId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Client ledger not found with ID: " + request.getClientLedgerId(),
                            "CLIENT_LEDGER_NOT_FOUND"
                    ));

            if (ledger.getLedgerType() != LedgerType.CUSTOMER) {
                throw new ValidationException(
                        "Selected client ledger must be a CUSTOMER ledger: " + ledger.getLedgerName(),
                        "ERR_INVALID_CLIENT_LEDGER_TYPE",
                        "clientLedgerId"
                );
            }

            if (!ledger.isActive()) {
                throw new ValidationException(
                        "Client ledger is inactive: " + ledger.getLedgerName(),
                        "ERR_CLIENT_LEDGER_INACTIVE",
                        "clientLedgerId"
                );
            }

            if (ledger.getLedgerGroup() == null
                    || ledger.getLedgerGroup().getGroupType() != LedgerGroupType.SUNDRY_DEBTORS) {
                throw new ValidationException(
                        "Client ledger must belong to SUNDRY_DEBTORS: " + ledger.getLedgerName(),
                        "ERR_INVALID_CLIENT_LEDGER_GROUP",
                        "clientLedgerId"
                );
            }

            if (request.getClientCompanyId() != null
                    && ledger.getCompany() != null
                    && !request.getClientCompanyId().equals(ledger.getCompany().getId())) {
                throw new ValidationException(
                        "Selected client ledger does not belong to the expense's client company",
                        "ERR_CLIENT_LEDGER_COMPANY_MISMATCH",
                        "clientLedgerId"
                );
            }

            log.info(
                    "[CLIENT-LEDGER-EXPLICIT-OVERRIDE-RESOLVED] operationExpenseId={} | ledgerId={} | " +
                            "ledgerName={} | clientCompanyId={}",
                    request.getOperationExpenseId(),
                    ledger.getId(),
                    ledger.getLedgerName(),
                    request.getClientCompanyId()
            );

            return ledger;
        }

        // =========================================================
        // PRIORITY 2 - COMPANY-LEVEL LOOKUP (NEW: unit removed)
        // =========================================================
        Long companyId = request.getClientCompanyId();

        if (companyId == null || companyId <= 0) {
            throw new ValidationException(
                    "Client company ID is required to resolve customer ledger",
                    "ERR_CLIENT_COMPANY_REQUIRED",
                    "clientCompanyId"
            );
        }

        List<LedgerMaster> existingLedgers =
                ledgerMasterRepository.findByCompanyIdAndLedgerTypeInAndDeletedFalse(
                        companyId,
                        List.of(LedgerType.CUSTOMER, LedgerType.CUSTOMER_ADVANCE)
                );

        if (existingLedgers != null && !existingLedgers.isEmpty()) {

            LedgerMaster customerLedger = existingLedgers.stream()
                    .filter(Objects::nonNull)
                    .filter(LedgerMaster::isActive)
                    .findFirst()
                    .orElse(existingLedgers.get(0));

            if (customerLedger.getLedgerType() != LedgerType.CUSTOMER) {
                throw new ValidationException(
                        "Company-funded government fee must use the normal CUSTOMER ledger, found: "
                                + customerLedger.getLedgerType(),
                        "ERR_INVALID_CLIENT_LEDGER_TYPE",
                        "clientCompanyId"
                );
            }

            if (!customerLedger.isActive()) {
                throw new ValidationException(
                        "Customer ledger is inactive: " + customerLedger.getLedgerName(),
                        "ERR_CLIENT_LEDGER_INACTIVE",
                        "clientCompanyId"
                );
            }

            if (customerLedger.getLedgerGroup() == null
                    || customerLedger.getLedgerGroup().getGroupType() != LedgerGroupType.SUNDRY_DEBTORS) {
                throw new ValidationException(
                        "Customer ledger must belong to SUNDRY_DEBTORS: " + customerLedger.getLedgerName(),
                        "ERR_INVALID_CLIENT_LEDGER_GROUP",
                        "clientCompanyId"
                );
            }

            log.info(
                    "[COMPANY-GOVT-FEE-CUSTOMER-LEDGER-REUSED] companyId={} | ledgerId={} | ledgerName={} | candidateCount={}",
                    companyId,
                    customerLedger.getId(),
                    customerLedger.getLedgerName(),
                    existingLedgers.size()
            );

            return customerLedger;
        }

        // =========================================================
        // PRIORITY 3 - NO LEDGER EXISTS YET, AUTO-CREATE ONE
        // =========================================================
        String companyName = firstNonBlank(
                request.getClientCompanyName(),
                request.getClientUnitName(),
                "Company-" + companyId
        );

        LedgerGroup sundryDebtorsGroup = getOrCreateLedgerGroupByType(LedgerGroupType.SUNDRY_DEBTORS);

        LedgerMaster newLedger = new LedgerMaster();
        newLedger.setLedgerName(companyName);
        newLedger.setLedgerCode(generateCustomerLedgerCode());
        newLedger.setLedgerType(LedgerType.CUSTOMER);
        newLedger.setLedgerGroup(sundryDebtorsGroup);
        newLedger.setOpeningBalance(zero());
        newLedger.setOpeningBalanceType(DebitCredit.DEBIT);
        newLedger.setCurrentBalance(zero());
        newLedger.setCurrentBalanceType(DebitCredit.DEBIT);
        newLedger.setSystemCreated(true);
        newLedger.setActive(true);
        newLedger.setDeleted(false);

        if (approver != null) {
            newLedger.setCreatedBy(approver);
            newLedger.setUpdatedBy(approver);
        }

        try {
            LedgerMaster saved = ledgerMasterRepository.saveAndFlush(newLedger);

            log.info(
                    "[COMPANY-GOVT-FEE-CUSTOMER-LEDGER-CREATED] companyId={} | ledgerId={} | ledgerName={} | ledgerCode={}",
                    companyId,
                    saved.getId(),
                    saved.getLedgerName(),
                    saved.getLedgerCode()
            );

            return saved;
        } catch (DataIntegrityViolationException exception) {
            // Concurrent request created the same company's ledger first. Re-fetch instead of failing.
            return ledgerMasterRepository
                    .findByCompanyIdAndLedgerTypeInAndDeletedFalse(
                            companyId,
                            List.of(LedgerType.CUSTOMER, LedgerType.CUSTOMER_ADVANCE)
                    )
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> exception);
        }
    }

    /**
     * Generates a unique ledger code for an auto-created CUSTOMER ledger,
     * following the same "LED-CUST-NNNNNN" convention used by
     * PaymentServiceImpl/InvoiceServiceImpl's getOrCreateCustomerLedger.
     */
    private String generateCustomerLedgerCode() {
        long sequence = ledgerMasterRepository.count() + 1;
        String code;
        do {
            code = String.format("LED-CUST-%06d", sequence++);
        } while (ledgerMasterRepository.existsByLedgerCodeIgnoreCase(code));
        return code;
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
            return validateAndActivateSystemLedger(
                    byCode.get(),
                    ledgerType,
                    groupType,
                    ledgerCode,
                    approver
            );
        }

        Optional<LedgerMaster> byType = ledgerMasterRepository
                .findByLedgerTypeAndDeletedFalse(ledgerType);

        if (byType.isPresent()) {
            LedgerMaster existing = byType.get();
            if (!ledgerCode.equalsIgnoreCase(existing.getLedgerCode())) {
                log.warn(
                        "[SYSTEM-LEDGER-CODE-NONCANONICAL] ledgerType={} | expectedCode={} | existingCode={} | ledgerId={}",
                        ledgerType,
                        ledgerCode,
                        existing.getLedgerCode(),
                        existing.getId()
                );
            }

            return validateAndActivateSystemLedger(
                    existing,
                    ledgerType,
                    groupType,
                    existing.getLedgerCode(),
                    approver
            );
        }

        LedgerGroup ledgerGroup = getOrCreateLedgerGroupByType(groupType);

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerName(ledgerName);
        ledger.setLedgerCode(ledgerCode);
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(ledgerGroup);
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
                    .or(() -> ledgerMasterRepository.findByLedgerTypeAndDeletedFalse(ledgerType))
                    .map(value -> validateAndActivateSystemLedger(
                            value,
                            ledgerType,
                            groupType,
                            value.getLedgerCode(),
                            approver
                    ))
                    .orElseThrow(() -> exception);
        }
    }

    private LedgerMaster validateAndActivateSystemLedger(
            LedgerMaster ledger,
            LedgerType expectedType,
            LedgerGroupType expectedGroupType,
            String expectedCode,
            User approver
    ) {
        if (ledger.getLedgerType() != expectedType) {
            throw new ValidationException(
                    "System ledger code " + expectedCode
                            + " is mapped to invalid ledger type " + ledger.getLedgerType()
                            + "; expected " + expectedType,
                    "ERR_SYSTEM_LEDGER_TYPE_MISMATCH",
                    "ledgerType"
            );
        }

        LedgerGroup group = ledger.getLedgerGroup();
        if (group == null || group.getGroupType() != expectedGroupType) {
            throw new ValidationException(
                    "System ledger " + ledger.getLedgerName()
                            + " is mapped to invalid ledger group. Expected "
                            + expectedGroupType,
                    "ERR_SYSTEM_LEDGER_GROUP_MISMATCH",
                    "ledgerGroup"
            );
        }

        boolean changed = false;

        if (!group.isActive() || group.isDeleted()) {
            group.setActive(true);
            group.setDeleted(false);
            group.setSystemDefault(true);
            ledgerGroupRepository.save(group);
        }

        if (!ledger.isSystemCreated()) {
            ledger.setSystemCreated(true);
            changed = true;
        }

        if (!ledger.isActive()) {
            ledger.setActive(true);
            changed = true;
        }

        if (ledger.isDeleted()) {
            ledger.setDeleted(false);
            changed = true;
        }

        if (approver != null) {
            ledger.setUpdatedBy(approver);
            changed = true;
        }

        return changed ? ledgerMasterRepository.save(ledger) : ledger;
    }

    private LedgerGroup getOrCreateLedgerGroupByType(
            LedgerGroupType groupType
    ) {
        if (groupType == null) {
            throw new ValidationException(
                    "Ledger group type is required",
                    "ERR_LEDGER_GROUP_TYPE_REQUIRED",
                    "groupType"
            );
        }

        Optional<LedgerGroup> existingActive = ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(groupType);

        if (existingActive.isPresent()) {
            LedgerGroup group = existingActive.get();
            if (!group.isActive() || !group.isSystemDefault()) {
                group.setActive(true);
                group.setSystemDefault(true);
                return ledgerGroupRepository.save(group);
            }
            return group;
        }

        Optional<LedgerGroup> existingAny = ledgerGroupRepository.findByGroupType(groupType);
        if (existingAny.isPresent()) {
            LedgerGroup group = existingAny.get();
            group.setDeleted(false);
            group.setActive(true);
            group.setSystemDefault(true);
            return ledgerGroupRepository.saveAndFlush(group);
        }

        LedgerGroup group = LedgerGroup.builder()
                .name(formatGroupTypeLabel(groupType))
                .groupType(groupType)
                .description("System default group for "
                        + formatGroupTypeLabel(groupType).toLowerCase(Locale.ROOT))
                .systemDefault(true)
                .active(true)
                .deleted(false)
                .build();

        try {
            LedgerGroup saved = ledgerGroupRepository.saveAndFlush(group);
            log.info(
                    "[LEDGER-GROUP-AUTO-CREATED] groupId={} | groupType={} | name={}",
                    saved.getId(),
                    saved.getGroupType(),
                    saved.getName()
            );
            return saved;
        } catch (DataIntegrityViolationException exception) {
            return ledgerGroupRepository.findByGroupType(groupType)
                    .map(existing -> {
                        existing.setDeleted(false);
                        existing.setActive(true);
                        existing.setSystemDefault(true);
                        return ledgerGroupRepository.saveAndFlush(existing);
                    })
                    .orElseThrow(() -> exception);
        }
    }

    private String formatGroupTypeLabel(LedgerGroupType groupType) {
        String[] words = groupType.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }
        return result.toString();
    }

    private LedgerMaster resolveVoucherBankLedger(
            AccountingVoucher voucher,
            DebitCredit side,
            String errorMessage
    ) {
        if (voucher == null || voucher.getEntries() == null) {
            throw new ValidationException(
                    errorMessage,
                    "ERR_EXISTING_VOUCHER_LEDGER_NOT_FOUND",
                    "voucher"
            );
        }

        return voucher.getEntries().stream()
                .filter(entry -> entry != null && entry.getLedger() != null)
                .filter(entry -> entry.getLedger().getLedgerType() == LedgerType.BANK)
                .filter(entry -> side == DebitCredit.DEBIT
                        ? isPositive(entry.getDebitAmount())
                        : isPositive(entry.getCreditAmount()))
                .map(AccountingVoucherEntry::getLedger)
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        errorMessage,
                        "ERR_EXISTING_VOUCHER_LEDGER_NOT_FOUND",
                        "voucher"
                ));
    }

    private LedgerMaster resolveVoucherLedgerBySide(
            AccountingVoucher voucher,
            DebitCredit side,
            String errorMessage
    ) {
        if (voucher == null || voucher.getEntries() == null) {
            throw new ValidationException(
                    errorMessage,
                    "ERR_EXISTING_VOUCHER_LEDGER_NOT_FOUND",
                    "voucher"
            );
        }

        return voucher.getEntries().stream()
                .filter(entry -> entry != null && entry.getLedger() != null)
                .filter(entry -> side == DebitCredit.DEBIT
                        ? isPositive(entry.getDebitAmount())
                        : isPositive(entry.getCreditAmount()))
                .map(AccountingVoucherEntry::getLedger)
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        errorMessage,
                        "ERR_EXISTING_VOUCHER_LEDGER_NOT_FOUND",
                        "voucher"
                ));
    }

    private LedgerMaster resolveVoucherLedgerByType(
            AccountingVoucher voucher,
            LedgerType ledgerType,
            DebitCredit side,
            String errorMessage
    ) {
        if (voucher == null || voucher.getEntries() == null) {
            throw new ValidationException(
                    errorMessage,
                    "ERR_EXISTING_VOUCHER_LEDGER_NOT_FOUND",
                    "voucher"
            );
        }

        return voucher.getEntries().stream()
                .filter(entry -> entry != null && entry.getLedger() != null)
                .filter(entry -> entry.getLedger().getLedgerType() == ledgerType)
                .filter(entry -> side == DebitCredit.DEBIT
                        ? isPositive(entry.getDebitAmount())
                        : isPositive(entry.getCreditAmount()))
                .map(AccountingVoucherEntry::getLedger)
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        errorMessage,
                        "ERR_EXISTING_VOUCHER_LEDGER_NOT_FOUND",
                        "voucher"
                ));
    }

    private boolean hasVoucherEntry(
            AccountingVoucher voucher,
            LedgerType ledgerType,
            DebitCredit side
    ) {
        if (voucher == null || voucher.getEntries() == null || ledgerType == null || side == null) {
            return false;
        }

        return voucher.getEntries().stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getLedger() != null)
                .filter(entry -> entry.getLedger().getLedgerType() == ledgerType)
                .anyMatch(entry -> side == DebitCredit.DEBIT
                        ? isPositive(entry.getDebitAmount())
                        : isPositive(entry.getCreditAmount()));
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
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
            LedgerMaster customerLedger,
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
                /*
                 * CLIENT_TO_COMPANY now uses the client's own CUSTOMER
                 * ledger instead of the pooled GOVERNMENT_FEE_CLIENT_ADVANCE
                 * liability ledger, so this field carries the customer
                 * ledger ID instead.
                 */
                .clientAdvanceLedgerId(
                        customerLedger != null
                                ? customerLedger.getId()
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

    private void validateFundTransferRequest(
            GovernmentFeeFundTransferPostingRequestDto request
    ) {
        if (request == null) {
            throw new ValidationException(
                    "Fund-transfer posting request is required",
                    "ERR_REQUEST_REQUIRED",
                    "request"
            );
        }

        validateOperationExpenseId(request.getOperationExpenseId());

        if (request.getFromBankLedgerId() == null
                || request.getFromBankLedgerId() <= 0
                || request.getToBankLedgerId() == null
                || request.getToBankLedgerId() <= 0) {
            throw new ValidationException(
                    "Both source and destination bank ledger IDs are required",
                    "ERR_BANK_LEDGER_REQUIRED",
                    "bankLedgerId"
            );
        }

        if (request.getFromBankLedgerId().equals(
                request.getToBankLedgerId())) {
            throw new ValidationException(
                    "Source and destination bank ledgers cannot be the same",
                    "ERR_SAME_BANK_TRANSFER",
                    "toBankLedgerId"
            );
        }

        validatePositiveAmount(request.getAmount(), "amount");

        if (request.getTransferDate() == null
                || request.getTransferDate().isAfter(LocalDate.now())) {
            throw new ValidationException(
                    "Transfer date is required and cannot be in the future",
                    "ERR_INVALID_TRANSFER_DATE",
                    "transferDate"
            );
        }

        requireText(
                request.getTransferReference(),
                "Transfer reference is required",
                "ERR_TRANSFER_REFERENCE_REQUIRED",
                "transferReference"
        );
    }

    private void validateGovernmentPaymentRequest(
            GovernmentFeePaymentPostingRequestDto request
    ) {
        if (request == null) {
            throw new ValidationException(
                    "Government-payment posting request is required",
                    "ERR_REQUEST_REQUIRED",
                    "request"
            );
        }

        validateOperationExpenseId(request.getOperationExpenseId());

        if (request.getPaidBy() == null
                || (request.getPaidBy() != GovernmentFeePaidBy.COMPANY
                && request.getPaidBy()
                != GovernmentFeePaidBy.CLIENT_TO_COMPANY)) {
            throw new ValidationException(
                    "Government payment requires COMPANY or CLIENT_TO_COMPANY funding",
                    "ERR_INVALID_PAID_BY",
                    "paidBy"
            );
        }

        if (request.getPaymentBankLedgerId() == null
                || request.getPaymentBankLedgerId() <= 0) {
            throw new ValidationException(
                    "Payment bank ledger ID is required",
                    "ERR_PAYMENT_BANK_REQUIRED",
                    "paymentBankLedgerId"
            );
        }

        validatePositiveAmount(request.getAmount(), "amount");

        if (!"INR".equalsIgnoreCase(clean(request.getCurrencyCode()))) {
            throw new ValidationException(
                    "Government-fee payment currently supports INR only",
                    "ERR_UNSUPPORTED_CURRENCY",
                    "currencyCode"
            );
        }

        if (request.getPaymentDate() == null
                || request.getPaymentDate().isAfter(LocalDate.now())) {
            throw new ValidationException(
                    "Payment date is required and cannot be in the future",
                    "ERR_INVALID_PAYMENT_DATE",
                    "paymentDate"
            );
        }

        String mode = clean(request.getPaymentMode());
        if (mode == null) {
            throw new ValidationException(
                    "Payment mode is required",
                    "ERR_PAYMENT_MODE_REQUIRED",
                    "paymentMode"
            );
        }

        mode = mode.toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');

        if (!GOVERNMENT_PAYMENT_MODES.contains(mode)) {
            throw new ValidationException(
                    "Unsupported government payment mode: " + mode,
                    "ERR_INVALID_PAYMENT_MODE",
                    "paymentMode"
            );
        }

        requireText(
                request.getPaymentReference(),
                "Payment reference is required",
                "ERR_PAYMENT_REFERENCE_REQUIRED",
                "paymentReference"
        );
        requireText(
                request.getPaymentReceiptUrl(),
                "Payment receipt URL is required",
                "ERR_PAYMENT_RECEIPT_REQUIRED",
                "paymentReceiptUrl"
        );
    }

    private void validateOperationExpenseId(Long operationExpenseId) {
        if (operationExpenseId == null || operationExpenseId <= 0) {
            throw new ValidationException(
                    "Operation expense ID must be greater than zero",
                    "ERR_OPERATION_EXPENSE_ID_REQUIRED",
                    "operationExpenseId"
            );
        }
    }

    private void validatePositiveAmount(
            BigDecimal amount,
            String field
    ) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Amount must be greater than zero",
                    "ERR_AMOUNT_INVALID",
                    field
            );
        }
    }

    private LedgerMaster resolveActiveBankLedger(
            Long ledgerId,
            String field
    ) {
        LedgerMaster ledger = ledgerMasterRepository
                .findByIdAndDeletedFalse(ledgerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank ledger not found with ID: " + ledgerId,
                        "LEDGER_NOT_FOUND"
                ));

        if (!ledger.isActive()) {
            throw new ValidationException(
                    "Bank ledger is inactive: " + ledger.getLedgerName(),
                    "ERR_BANK_LEDGER_INACTIVE",
                    field
            );
        }

        if (ledger.getLedgerType() != LedgerType.BANK) {
            throw new ValidationException(
                    "Ledger must be a BANK ledger: "
                            + ledger.getLedgerName(),
                    "ERR_INVALID_BANK_LEDGER_TYPE",
                    field
            );
        }

        return ledger;
    }

    private LedgerMaster resolveGovernmentFeePayableLedger() {
        LedgerMaster payable = ledgerMasterRepository
                .findByLedgerCodeIgnoreCaseAndDeletedFalse(
                        GOVERNMENT_FEE_PAYABLE_CODE
                )
                .orElseGet(() -> ledgerMasterRepository
                        .findByLedgerTypeAndDeletedFalse(
                                LedgerType.GOVERNMENT_FEE_PAYABLE
                        )
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Government Fee Payable ledger does not exist. Complete Step 3 first.",
                                "GOVERNMENT_FEE_PAYABLE_LEDGER_NOT_FOUND"
                        )));

        if (!payable.isActive()) {
            throw new ValidationException(
                    "Government Fee Payable ledger is inactive",
                    "ERR_GOVERNMENT_FEE_PAYABLE_INACTIVE",
                    "governmentFeePayableLedgerId"
            );
        }

        if (payable.getLedgerType() != LedgerType.GOVERNMENT_FEE_PAYABLE) {
            throw new ValidationException(
                    "Government Fee Payable ledger has invalid ledger type: "
                            + payable.getLedgerType(),
                    "ERR_GOVERNMENT_FEE_PAYABLE_TYPE_MISMATCH",
                    "governmentFeePayableLedgerId"
            );
        }

        if (payable.getLedgerGroup() == null
                || payable.getLedgerGroup().getGroupType()
                != LedgerGroupType.CURRENT_LIABILITIES) {
            throw new ValidationException(
                    "Government Fee Payable ledger must belong to CURRENT_LIABILITIES",
                    "ERR_GOVERNMENT_FEE_PAYABLE_GROUP_MISMATCH",
                    "governmentFeePayableLedgerId"
            );
        }

        return payable;
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

        log.info(
                "[ACC-CLIENT-FUNDING-VALIDATION-START] operationExpenseId={} | clientCompanyId={} | " +
                        "clientUnitId={} | paymentMode={} | bankLedgerId={} | bankName={} | paymentDate={} | " +
                        "referencePresent={} | proofPresent={}",
                request.getOperationExpenseId(),
                request.getClientCompanyId(),
                request.getClientUnitId(),
                request.getClientPaymentMode(),
                request.getClientPaymentBankLedgerId(),
                request.getClientPaymentBankName(),
                request.getClientPaymentDate(),
                hasText(request.getClientPaymentReference()),
                hasText(request.getClientPaymentProofUrl())
        );

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

        log.info(
                "[ACC-CLIENT-FUNDING-VALIDATION-SUCCESS] operationExpenseId={} | normalizedPaymentMode={} | bankLedgerId={}",
                request.getOperationExpenseId(),
                mode,
                request.getClientPaymentBankLedgerId()
        );
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

    private String safeProjectNumber(
            String projectNo,
            Long projectId
    ) {
        String cleaned = clean(projectNo);
        return cleaned != null
                ? cleaned
                : String.valueOf(projectId);
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }



    @Override
    @Transactional(readOnly = true)
    public Page<GovernmentExpenseListItemDto> getGovernmentFeeExpenses(
            Pageable pageable
    ) {

        log.info(
                "[GOVERNMENT-EXPENSE-LIST-START] page={} | size={}",
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Page<AccountingVoucher> accrualVouchers =
                accountingVoucherRepository.findBySourceTypeAndStatus(
                        VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                        VoucherStatus.POSTED,
                        pageable
                );

        return accrualVouchers.map(this::mapGovernmentExpenseListItem);
    }

    private GovernmentExpenseListItemDto mapGovernmentExpenseListItem(
            AccountingVoucher accrualVoucher
    ) {
        Long operationExpenseId = accrualVoucher.getSourceId();

        Optional<AccountingVoucher> fundTransfer =
                findPostedVoucher(
                        VoucherSourceType.PROJECT_EXPENSE_FUND_TRANSFER,
                        operationExpenseId
                );

        Optional<AccountingVoucher> payment =
                findPostedVoucher(
                        VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_PAYMENT,
                        operationExpenseId
                );

        LedgerMaster partyLedger = resolvePartyLedgerFromVoucher(
                accrualVoucher
        );

        Long clientCompanyId = resolveClientCompanyId(
                accrualVoucher,
                partyLedger
        );
        String clientCompanyName = resolveClientCompanyName(
                accrualVoucher,
                partyLedger
        );
        Long clientUnitId = resolveClientUnitId(
                accrualVoucher,
                partyLedger
        );
        String clientUnitName = resolveClientUnitName(
                accrualVoucher,
                partyLedger
        );

        return GovernmentExpenseListItemDto.builder()
                .operationExpenseId(operationExpenseId)
                .voucherId(accrualVoucher.getId())
                .voucherNumber(accrualVoucher.getVoucherNumber())
                .voucherDate(accrualVoucher.getVoucherDate())
                .projectId(accrualVoucher.getProjectId())
                .projectNo(accrualVoucher.getProjectNo())
                .projectName(accrualVoucher.getProjectName())
                .clientCompanyId(clientCompanyId)
                .clientCompanyName(clientCompanyName)
                .clientUnitId(clientUnitId)
                .clientUnitName(clientUnitName)
                .expensePaidBy(accrualVoucher.getExpensePaidBy())
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
                .amount(accrualVoucher.getTotalDebit())
                .status(accrualVoucher.getStatus().name())
                .narration(accrualVoucher.getNarration())
                .entries(mapVoucherEntries(accrualVoucher))
                .fundTransferPosted(fundTransfer.isPresent())
                .paymentPosted(payment.isPresent())
                .fundTransferVoucherId(
                        fundTransfer.map(AccountingVoucher::getId).orElse(null)
                )
                .paymentVoucherId(
                        payment.map(AccountingVoucher::getId).orElse(null)
                )
                .postedAt(resolvePostedAt(accrualVoucher))
                .build();
    }
    private void validateCompanyFundedClientDetails(
            GovernmentFeePostingRequestDto request
    ) {

        if (request.getClientCompanyId() == null
                || request.getClientCompanyId() <= 0) {

            throw new ValidationException(
                    "Client company ID is required when company funds government fee on behalf of client",
                    "ERR_CLIENT_COMPANY_REQUIRED",
                    "clientCompanyId"
            );
        }

        if (request.getClientUnitId() == null
                || request.getClientUnitId() <= 0) {

            throw new ValidationException(
                    "Client unit ID is required when company funds government fee on behalf of client",
                    "ERR_CLIENT_UNIT_REQUIRED",
                    "clientUnitId"
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GovernmentExpenseVoucherListItemDto> getGovernmentFeeVouchers(
            Pageable pageable
    ) {

        log.info(
                "[GOVERNMENT-FEE-VOUCHER-LIST] page={} | size={}",
                pageable.getPageNumber(),
                pageable.getPageSize()
        );

        Page<AccountingVoucher> vouchers =
                accountingVoucherRepository.findBySourceTypeInAndStatus(
                        GOVERNMENT_FEE_VOUCHER_SOURCE_TYPES,
                        VoucherStatus.POSTED,
                        pageable
                );

        return vouchers.map(this::mapGovernmentFeeVoucherListItem);
    }

    private GovernmentExpenseVoucherListItemDto
    mapGovernmentFeeVoucherListItem(AccountingVoucher voucher) {

        AccountingVoucher contextVoucher = resolveContextVoucher(voucher);
        LedgerMaster partyLedger = resolvePartyLedgerFromVoucher(
                contextVoucher
        );

        return GovernmentExpenseVoucherListItemDto.builder()
                .voucherId(voucher.getId())
                .voucherNumber(voucher.getVoucherNumber())
                .voucherType(voucher.getVoucherType())
                .voucherDate(voucher.getVoucherDate())
                .operationExpenseId(voucher.getSourceId())
                .sourceType(voucher.getSourceType())
                .status(voucher.getStatus())
                .projectId(contextVoucher.getProjectId())
                .projectNo(contextVoucher.getProjectNo())
                .projectName(contextVoucher.getProjectName())
                .clientCompanyId(
                        resolveClientCompanyId(contextVoucher, partyLedger)
                )
                .clientCompanyName(
                        resolveClientCompanyName(contextVoucher, partyLedger)
                )
                .clientUnitId(
                        resolveClientUnitId(contextVoucher, partyLedger)
                )
                .clientUnitName(
                        resolveClientUnitName(contextVoucher, partyLedger)
                )
                .expensePaidBy(contextVoucher.getExpensePaidBy())
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
                .amount(voucher.getTotalDebit())
                .totalDebit(voucher.getTotalDebit())
                .totalCredit(voucher.getTotalCredit())
                .narration(voucher.getNarration())
                .entries(mapVoucherEntries(voucher))
                .createdAt(voucher.getCreatedAt())
                .build();
    }

    private AccountingVoucher resolveContextVoucher(
            AccountingVoucher voucher
    ) {
        if (voucher == null) {
            return null;
        }

        if (voucher.getSourceType()
                == VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL) {
            return voucher;
        }

        return findPostedVoucher(
                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                voucher.getSourceId()
        ).orElse(voucher);
    }

    private List<AccountingVoucherEntryResponseDto> mapVoucherEntries(
            AccountingVoucher voucher
    ) {
        if (voucher == null || voucher.getEntries() == null) {
            return List.of();
        }

        return voucher.getEntries()
                .stream()
                .sorted(Comparator.comparing(
                        entry -> entry.getDisplayOrder() != null
                                ? entry.getDisplayOrder()
                                : 0
                ))
                .map(entry -> {
                    LedgerMaster ledger = entry.getLedger();

                    return AccountingVoucherEntryResponseDto.builder()
                            .id(entry.getId())
                            .ledgerId(
                                    ledger != null ? ledger.getId() : null
                            )
                            .ledgerName(
                                    ledger != null
                                            ? ledger.getLedgerName()
                                            : null
                            )
                            .ledgerCode(
                                    ledger != null
                                            ? ledger.getLedgerCode()
                                            : null
                            )
                            .ledgerType(
                                    ledger != null
                                            ? ledger.getLedgerType()
                                            : null
                            )
                            .debitAmount(entry.getDebitAmount())
                            .creditAmount(entry.getCreditAmount())
                            .narration(entry.getNarration())
                            .displayOrder(entry.getDisplayOrder())
                            .build();
                })
                .toList();
    }

    private LedgerMaster resolvePartyLedgerFromVoucher(
            AccountingVoucher voucher
    ) {
        if (voucher == null) {
            return null;
        }

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

    private Long resolveClientCompanyId(
            AccountingVoucher voucher,
            LedgerMaster partyLedger
    ) {
        if (voucher.getClientCompanyId() != null) {
            return voucher.getClientCompanyId();
        }

        return partyLedger != null && partyLedger.getCompany() != null
                ? partyLedger.getCompany().getId()
                : null;
    }

    private String resolveClientCompanyName(
            AccountingVoucher voucher,
            LedgerMaster partyLedger
    ) {
        String snapshot = clean(voucher.getClientCompanyName());
        if (snapshot != null) {
            return snapshot;
        }

        return partyLedger != null && partyLedger.getCompany() != null
                ? partyLedger.getCompany().getName()
                : null;
    }

    private Long resolveClientUnitId(
            AccountingVoucher voucher,
            LedgerMaster partyLedger
    ) {
        if (voucher.getClientUnitId() != null) {
            return voucher.getClientUnitId();
        }

        return partyLedger != null && partyLedger.getUnit() != null
                ? partyLedger.getUnit().getId()
                : null;
    }

    private String resolveClientUnitName(
            AccountingVoucher voucher,
            LedgerMaster partyLedger
    ) {
        String snapshot = clean(voucher.getClientUnitName());
        if (snapshot != null) {
            return snapshot;
        }

        return partyLedger != null && partyLedger.getUnit() != null

                ? partyLedger.getUnit().getUnitName()
                : null;
    }


    /**
     * Creates only the missing CUSTOMER debit-note voucher for CLIENT_TO_COMPANY.
     *
     * No new VoucherSourceType is introduced. The existing
     * PROJECT_EXPENSE_GOVT_FEE_ACCRUAL source is reused for this balancing voucher:
     *
     * Dr CUSTOMER
     * Cr GOVERNMENT_FEE_RECEIVABLE
     *
     * Existing receipt / accrual / contra / government-payment vouchers remain
     * untouched and continue to use their existing source types.
     */
    private AccountingVoucher getOrCreateClientGovernmentFeeCustomerDebitNote(
            GovernmentFeePostingRequestDto request,
            LedgerMaster customerLedger,
            LedgerMaster receivableLedger,
            BigDecimal amount
    ) {

        BigDecimal postingAmount = money(amount);

        Optional<AccountingVoucher> existing =
                findPostedVoucher(
                        VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL,
                        request.getOperationExpenseId()
                );

        if (existing.isPresent()) {

            AccountingVoucher voucher = existing.get();

            validateExistingVoucherAmount(
                    existing,
                    postingAmount,
                    "Existing customer government-fee debit note amount differs from approved amount"
            );

            LedgerMaster existingCustomer = resolveVoucherLedgerByType(
                    voucher,
                    LedgerType.CUSTOMER,
                    DebitCredit.DEBIT,
                    "Existing government-fee accrual voucher does not contain CUSTOMER debit entry"
            );

            LedgerMaster existingReceivable = resolveVoucherLedgerByType(
                    voucher,
                    LedgerType.GOVERNMENT_FEE_RECEIVABLE,
                    DebitCredit.CREDIT,
                    "Existing government-fee accrual voucher does not contain Government Fee Receivable credit entry"
            );

            if (!Objects.equals(existingCustomer.getId(), customerLedger.getId())) {
                throw new ValidationException(
                        "Existing customer debit-note voucher belongs to another customer ledger",
                        "ERR_GOVT_FEE_CUSTOMER_DEBIT_NOTE_LEDGER_MISMATCH",
                        "clientLedgerId"
                );
            }

            if (!Objects.equals(existingReceivable.getId(), receivableLedger.getId())) {
                throw new ValidationException(
                        "Existing customer debit-note voucher has a different Government Fee Receivable ledger",
                        "ERR_GOVT_FEE_RECEIVABLE_LEDGER_MISMATCH",
                        "operationExpenseId"
                );
            }

            log.info(
                    "[ACC-CLIENT-GOVT-FEE-DEBIT-NOTE-ALREADY-POSTED] " +
                            "operationExpenseId={} | voucherId={} | voucherNumber={} | " +
                            "customerLedgerId={} | receivableLedgerId={} | amount={}",
                    request.getOperationExpenseId(),
                    voucher.getId(),
                    voucher.getVoucherNumber(),
                    customerLedger.getId(),
                    receivableLedger.getId(),
                    postingAmount
            );

            return voucher;
        }

        return createClientGovernmentFeeCustomerDebitNote(
                request,
                customerLedger,
                receivableLedger,
                postingAmount
        );
    }


    private AccountingVoucher createClientGovernmentFeeCustomerDebitNote(
            GovernmentFeePostingRequestDto request,
            LedgerMaster customerLedger,
            LedgerMaster receivableLedger,
            BigDecimal amount
    ) {

        BigDecimal postingAmount = money(amount);

        if (customerLedger == null) {
            throw new ValidationException(
                    "Customer ledger is required for government-fee debit note",
                    "ERR_CUSTOMER_LEDGER_REQUIRED",
                    "clientLedgerId"
            );
        }

        if (customerLedger.getLedgerType() != LedgerType.CUSTOMER) {
            throw new ValidationException(
                    "Government-fee debit note requires CUSTOMER ledger. Found: "
                            + customerLedger.getLedgerType(),
                    "ERR_INVALID_CUSTOMER_LEDGER",
                    "clientLedgerId"
            );
        }

        if (receivableLedger == null) {
            throw new ValidationException(
                    "Government Fee Receivable ledger is required",
                    "ERR_GOVERNMENT_FEE_RECEIVABLE_REQUIRED",
                    "operationExpenseId"
            );
        }

        if (receivableLedger.getLedgerType()
                != LedgerType.GOVERNMENT_FEE_RECEIVABLE) {

            throw new ValidationException(
                    "Expected GOVERNMENT_FEE_RECEIVABLE ledger but found: "
                            + receivableLedger.getLedgerType(),
                    "ERR_INVALID_GOVERNMENT_FEE_RECEIVABLE",
                    "operationExpenseId"
            );
        }

        String customerName =
                firstNonBlank(
                        request.getClientCompanyName(),
                        request.getClientUnitName(),
                        customerLedger.getLedgerName(),
                        "Client-" + request.getClientCompanyId()
                );

        log.info(
                "[ACC-CLIENT-GOVT-FEE-DEBIT-NOTE-CREATE-START] " +
                        "operationExpenseId={} | customerLedgerId={} | customerLedgerName={} | " +
                        "receivableLedgerId={} | receivableLedgerName={} | amount={}",
                request.getOperationExpenseId(),
                customerLedger.getId(),
                customerLedger.getLedgerName(),
                receivableLedger.getId(),
                receivableLedger.getLedgerName(),
                postingAmount
        );

        /*
         * =====================================================
         * ONLY MISSING ENTRY
         * =====================================================
         *
         * Dr CUSTOMER
         * Cr GOVERNMENT_FEE_RECEIVABLE
         *
         * DO NOT TOUCH GOVERNMENT_FEE_PAYABLE HERE.
         *
         * Under the new CLIENT_TO_COMPANY requirement, Step 3 does not
         * post to GOVERNMENT_FEE_PAYABLE. Step 5 continues to post:
         *
         * Dr Government Fee Payable
         * Cr Payment Bank
         * =====================================================
         */

        AccountingVoucherRequestDto voucherRequest =
                AccountingVoucherRequestDto.builder()

                        .voucherType(
                                VoucherType.DEBIT_NOTE
                        )

                        .voucherDate(
                                resolvePostingDate(request)
                        )

                        /*
                         * NO NEW ENUM.
                         *
                         * Existing PROJECT_EXPENSE_GOVT_FEE_ACCRUAL is reused
                         * for this customer debit-note accrual.
                         */
                        .sourceType(
                                VoucherSourceType.PROJECT_EXPENSE_GOVT_FEE_ACCRUAL
                        )

                        .sourceId(
                                request.getOperationExpenseId()
                        )

                        .projectId(
                                request.getProjectId()
                        )

                        .projectNo(
                                request.getProjectNo()
                        )

                        .projectName(
                                request.getProjectName()
                        )

                        .clientCompanyId(
                                request.getClientCompanyId()
                        )

                        .clientCompanyName(
                                request.getClientCompanyName()
                        )

                        .clientUnitId(
                                request.getClientUnitId()
                        )

                        .clientUnitName(
                                request.getClientUnitName()
                        )

                        .expensePaidBy(
                                request.getPaidBy().name()
                        )

                        /*
                         * Debit Note belongs to Infosys/customer.
                         */
                        .partyLedgerId(
                                customerLedger.getId()
                        )

                        .narration(
                                "Government fee debit note for "
                                        + customerName
                                        + " | project "
                                        + safeProjectNumber(request)
                        )

                        .entries(
                                List.of(

                                        /*
                                         * DR CUSTOMER
                                         *
                                         * THIS IS THE MISSING INFOSYS ENTRY.
                                         */
                                        debitEntry(
                                                customerLedger.getId(),
                                                postingAmount,
                                                "Government Fee Receivable"
                                        ),

                                        /*
                                         * CR GOVERNMENT FEE RECEIVABLE
                                         *
                                         * Balancing side of Debit Note.
                                         *
                                         * IMPORTANT:
                                         * Government Fee Payable is NOT used here.
                                         */
                                        creditEntry(
                                                receivableLedger.getId(),
                                                postingAmount,
                                                "Government fee receivable from "
                                                        + customerName
                                        )
                                )
                        )

                        .build();

        log.info(
                "[ACC-CLIENT-GOVT-FEE-DEBIT-NOTE-ENTRIES] " +
                        "operationExpenseId={} | " +
                        "DR[customerLedgerId={}, customerName={}, amount={}] | " +
                        "CR[receivableLedgerId={}, receivableName={}, amount={}]",
                request.getOperationExpenseId(),

                customerLedger.getId(),
                customerLedger.getLedgerName(),
                postingAmount,

                receivableLedger.getId(),
                receivableLedger.getLedgerName(),
                postingAmount
        );

        AccountingVoucherResponseDto response =
                accountingVoucherService.createVoucher(
                        voucherRequest
                );

        if (response == null || response.getId() == null) {
            throw new ValidationException(
                    "Customer government-fee debit note was not created",
                    "ERR_GOVT_FEE_CUSTOMER_DEBIT_NOTE_CREATION_FAILED",
                    "operationExpenseId"
            );
        }

        AccountingVoucher voucher =
                getCreatedVoucher(
                        response.getId()
                );

        log.info(
                "[ACC-CLIENT-GOVT-FEE-DEBIT-NOTE-CREATED] " +
                        "operationExpenseId={} | voucherId={} | voucherNumber={} | " +
                        "DR_CUSTOMER={} | CR_GOVT_RECEIVABLE={} | amount={}",
                request.getOperationExpenseId(),
                voucher.getId(),
                voucher.getVoucherNumber(),
                customerLedger.getId(),
                receivableLedger.getId(),
                postingAmount
        );

        return voucher;
    }


}
