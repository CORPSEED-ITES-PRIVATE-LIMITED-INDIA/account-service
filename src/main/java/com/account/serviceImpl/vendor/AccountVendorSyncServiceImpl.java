package com.account.serviceImpl.vendor;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.ledger.*;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Service vendor payment synchronization.
 *
 * Receives an immutable GST/TDS calculation snapshot from Operation Service and
 * creates:
 *   1. PURCHASE_INVOICE voucher (vendor invoice booking)
 *   2. PAYMENT voucher (bank settlement + TDS receivable), when release fields present
 *
 * Account Service VALIDATES the snapshot and posts vouchers. It NEVER
 * recalculates GST or TDS. Vendor credit is always the exact arithmetic sum of
 * the debit legs so the double-entry voucher always balances regardless of the
 * separately-supplied gross/settlement totals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountVendorSyncServiceImpl implements AccountVendorSyncService {

    private static final String LOG = "[ACCOUNT-VENDOR-SYNC]";
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);

    private final AccountingVoucherService accountingVoucherService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final LedgerMasterRepository ledgerMasterRepository;
    private final LedgerGroupRepository ledgerGroupRepository;

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public AccountVendorSyncResponseDto syncVendor(AccountVendorSyncRequestDto request) {

        String traceId = "VENDOR-SYNC-" + UUID.randomUUID();

        log.info(
                "{} SYNC-START | traceId={} | operationVendorId={} | "
                        + "vendorAccountsSubmissionId={} | paymentApprovalPresent={}",
                LOG,
                traceId,
                request != null ? request.getOperationVendorId() : null,
                request != null ? request.getVendorAccountsSubmissionId() : null,
                request != null && request.getPaymentApproval() != null
        );

        // 1. BASIC REQUEST VALIDATION
        validateSyncRequest(request);

        Long operationVendorId = request.getOperationVendorId();
        String vendorName = request.getVendorName();
        String gstNumber = request.getGstNumber();
        String panNumber = request.getPan();

        GstRegistrationType gstRegistrationType =
                resolveGstRegistrationType(request.getGstRegistrationType());

        // 2. VALIDATE PAYMENT SNAPSHOT (validate-only, no recalculation)
        VendorPaymentApprovalRequestDto snapshot = request.getPaymentApproval();
        if (snapshot != null) {
            validatePaymentSnapshot(snapshot, gstRegistrationType);
        }

        // 3. RESOLVE OR CREATE VENDOR LEDGER (deterministic by code VEN-<id>)
        LedgerMaster vendorLedger = resolveOrCreateVendorLedger(
                traceId, operationVendorId, vendorName, gstNumber, panNumber
        );

        // 4. POST PURCHASE_INVOICE VOUCHER (idempotent)
        Long purchaseInvoiceVoucherId = null;
        if (snapshot != null) {
            purchaseInvoiceVoucherId =
                    postPurchaseInvoiceVoucher(traceId, snapshot, vendorLedger, gstRegistrationType);
        }

        // 5. POST PAYMENT VOUCHER (idempotent) when release data is present
        Long paymentVoucherId = null;
        if (snapshot != null
                && snapshot.getBankPaymentAmount() != null
                && snapshot.getBankPaymentAmount().compareTo(BigDecimal.ZERO) > 0
                && snapshot.getPaymentReleasedByOperationUserId() != null) {

            paymentVoucherId =
                    postPaymentVoucher(traceId, snapshot, vendorLedger, gstRegistrationType);
        }

        // 6. BUILD RESPONSE
        AccountVendorSyncResponseDto response = AccountVendorSyncResponseDto.builder()
                .operationVendorId(operationVendorId)
                .vendorAccountsSubmissionId(request.getVendorAccountsSubmissionId())
                .vendorFinalizationId(request.getVendorFinalizationId())
                .vendorName(vendorName)
                .ledgerId(vendorLedger.getId())
                .ledgerCode(vendorLedger.getLedgerCode())
                .ledgerName(vendorLedger.getLedgerName())
                .ledgerType(vendorLedger.getLedgerType().name())
                .ledgerGroupId(vendorLedger.getLedgerGroup() != null
                        ? vendorLedger.getLedgerGroup().getId() : null)
                .ledgerGroupName(vendorLedger.getLedgerGroup() != null
                        ? vendorLedger.getLedgerGroup().getName() : null)
                .ledgerGroupType(vendorLedger.getLedgerGroup() != null
                        && vendorLedger.getLedgerGroup().getGroupType() != null
                        ? vendorLedger.getLedgerGroup().getGroupType().name() : null)
                .action("SYNC")
                .active(vendorLedger.isActive())
                .voucherCreated(purchaseInvoiceVoucherId != null)
                .voucherId(purchaseInvoiceVoucherId)
                .paymentVoucherCreated(paymentVoucherId != null)
                .paymentVoucherId(paymentVoucherId)
                .syncStatus("SUCCESS")
                .syncedAt(LocalDateTime.now())
                .message("Vendor payment synchronized successfully")
                .build();

        log.info(
                "{} SYNC-SUCCESS | traceId={} | operationVendorId={} | vendorLedgerId={} | "
                        + "purchaseInvoiceVoucherId={} | paymentVoucherId={}",
                LOG, traceId, operationVendorId, vendorLedger.getId(),
                purchaseInvoiceVoucherId, paymentVoucherId
        );

        return response;
    }

    // =====================================================================
    // VALIDATION
    // =====================================================================

    private void validateSyncRequest(AccountVendorSyncRequestDto request) {
        if (request == null) {
            throw new ValidationException(
                    "Account vendor sync request is required",
                    "ERR_VENDOR_SYNC_REQUEST_REQUIRED", "request");
        }
        if (request.getOperationVendorId() == null) {
            throw new ValidationException(
                    "Operation vendor ID is required",
                    "ERR_OPERATION_VENDOR_ID_REQUIRED", "operationVendorId");
        }
        if (request.getVendorName() == null || request.getVendorName().trim().isEmpty()) {
            throw new ValidationException(
                    "Vendor name is required",
                    "ERR_VENDOR_NAME_REQUIRED", "vendorName");
        }
    }

    /**
     * Validates the snapshot arithmetic. Does NOT recalculate.
     */
    private void validatePaymentSnapshot(
            VendorPaymentApprovalRequestDto s,
            GstRegistrationType gstType
    ) {
        // Invoice composition
        if (s.getInvoiceGrossAmount() == null
                || s.getInvoiceGrossAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Invoice gross amount must be greater than zero",
                    "ERR_INVOICE_AMOUNT_INVALID", "invoiceGrossAmount");
        }
        if (s.getTaxableAmount() == null
                || s.getTaxableAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Taxable amount must be greater than zero",
                    "ERR_TAXABLE_AMOUNT_INVALID", "taxableAmount");
        }
        if (s.getTotalGstAmount() == null) {
            throw new ValidationException(
                    "Total GST amount is required",
                    "ERR_TOTAL_GST_AMOUNT_REQUIRED", "totalGstAmount");
        }

        BigDecimal taxable = money(s.getTaxableAmount());
        BigDecimal totalGst = money(s.getTotalGstAmount());
        BigDecimal invoiceGross = money(s.getInvoiceGrossAmount());

        if (taxable.add(totalGst).compareTo(invoiceGross) != 0) {
            throw new ValidationException(
                    "Taxable + GST does not equal invoice gross. Taxable: Rs. "
                            + taxable.toPlainString() + ", GST: Rs. " + totalGst.toPlainString()
                            + ", invoice: Rs. " + invoiceGross.toPlainString(),
                    "ERR_INVOICE_COMPOSITION_MISMATCH", "invoiceGrossAmount");
        }

        // GST split consistency
        BigDecimal cgst = money(s.getCgstAmount());
        BigDecimal sgst = money(s.getSgstAmount());
        BigDecimal igst = money(s.getIgstAmount());

        boolean zeroRated = gstType == GstRegistrationType.SEZ
                || gstType == GstRegistrationType.INTERNATIONAL;

        if (zeroRated) {
            if (totalGst.compareTo(BigDecimal.ZERO) != 0) {
                throw new ValidationException(
                        gstType + " transaction must be zero-rated (GST = 0). GST: Rs. "
                                + totalGst.toPlainString(),
                        "ERR_GST_NOT_ZERO_FOR_ZERO_RATED", "totalGstAmount");
            }
        } else {
            if (cgst.add(sgst).add(igst).compareTo(totalGst) != 0) {
                throw new ValidationException(
                        "CGST + SGST + IGST does not equal total GST. Split: Rs. "
                                + cgst.add(sgst).add(igst).toPlainString()
                                + ", totalGst: Rs. " + totalGst.toPlainString(),
                        "ERR_GST_SPLIT_MISMATCH", "totalGstAmount");
            }
        }

        // TDS rules
        if (gstType == GstRegistrationType.INTERNATIONAL) {
            if (Boolean.TRUE.equals(s.getTdsActive())
                    || (s.getTdsAmount() != null
                    && s.getTdsAmount().compareTo(BigDecimal.ZERO) > 0)) {
                throw new ValidationException(
                        "TDS cannot apply to INTERNATIONAL transactions",
                        "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL", "tdsActive");
            }
        } else if (Boolean.TRUE.equals(s.getTdsActive())) {
            if (s.getTdsAmount() == null
                    || s.getTdsAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException(
                        "TDS amount is required when TDS is active",
                        "ERR_TDS_AMOUNT_REQUIRED", "tdsAmount");
            }
            if (s.getTdsPercentage() == null
                    || s.getTdsPercentage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException(
                        "TDS percentage is required when TDS is active",
                        "ERR_TDS_PERCENTAGE_REQUIRED", "tdsPercentage");
            }
        }

        // Payable / settlement arithmetic (only when payment release present)
        if (s.getBankPaymentAmount() != null
                && s.getBankPaymentAmount().compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal bank = money(s.getBankPaymentAmount());
            BigDecimal tds = money(s.getTdsAmount());
            BigDecimal netPayable = money(s.getVendorNetPayableAmount());
            BigDecimal settlement = money(s.getSettlementAmount());

            if (invoiceGross.subtract(tds).compareTo(netPayable) != 0) {
                throw new ValidationException(
                        "Invoice - TDS does not equal vendor net payable. Invoice: Rs. "
                                + invoiceGross.toPlainString() + ", TDS: Rs. " + tds.toPlainString()
                                + ", netPayable: Rs. " + netPayable.toPlainString(),
                        "ERR_VENDOR_PAYABLE_MISMATCH", "vendorNetPayableAmount");
            }
            if (bank.add(tds).compareTo(settlement) != 0) {
                throw new ValidationException(
                        "Bank + TDS does not equal settlement. Bank: Rs. "
                                + bank.toPlainString() + ", TDS: Rs. " + tds.toPlainString()
                                + ", settlement: Rs. " + settlement.toPlainString(),
                        "ERR_SETTLEMENT_MISMATCH", "settlementAmount");
            }
            if (bank.compareTo(netPayable) != 0) {
                throw new ValidationException(
                        "Bank payment must equal vendor net payable (full settlement). Bank: Rs. "
                                + bank.toPlainString() + ", netPayable: Rs. " + netPayable.toPlainString(),
                        "ERR_BANK_PAYMENT_NOT_FULL_SETTLEMENT", "bankPaymentAmount");
            }
        }

        log.debug(
                "{} SNAPSHOT-VALIDATED | invoiceGross={} | taxable={} | totalGst={} | "
                        + "cgst={} | sgst={} | igst={} | tds={} | gstType={}",
                LOG, invoiceGross, taxable, totalGst, cgst, sgst, igst,
                money(s.getTdsAmount()), gstType);
    }

    // =====================================================================
    // LEDGER RESOLUTION
    // =====================================================================

    private LedgerMaster resolveOrCreateVendorLedger(
            String traceId,
            Long operationVendorId,
            String vendorName,
            String gstNumber,
            String panNumber
    ) {
        String vendorCode = generateVendorLedgerCode(operationVendorId);

        Optional<LedgerMaster> existing =
                ledgerMasterRepository.findByLedgerCodeIgnoreCaseAndDeletedFalse(vendorCode);

        if (existing.isPresent()) {
            LedgerMaster ledger = existing.get();
            log.info(
                    "{} VENDOR-LEDGER-RESOLVED | traceId={} | operationVendorId={} | "
                            + "ledgerId={} | ledgerCode={} | ledgerType={}",
                    LOG, traceId, operationVendorId,
                    ledger.getId(), ledger.getLedgerCode(), ledger.getLedgerType());
            return ledger;
        }

        LedgerGroup vendorGroup = ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(LedgerGroupType.SUNDRY_CREDITORS)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sundry Creditors ledger group not found",
                        "SUNDRY_CREDITORS_GROUP_NOT_FOUND"));

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerCode(vendorCode);
        ledger.setLedgerName(vendorName.trim());
        ledger.setLedgerType(LedgerType.VENDOR);
        ledger.setLedgerGroup(vendorGroup);
        ledger.setGstNo(gstNumber);
        ledger.setPanNo(panNumber);
        ledger.setOpeningBalance(ZERO);
        ledger.setOpeningBalanceType(DebitCredit.CREDIT);
        ledger.setCurrentBalance(ZERO);
        ledger.setCurrentBalanceType(DebitCredit.CREDIT);
        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        LedgerMaster saved = ledgerMasterRepository.save(ledger);

        log.info(
                "{} VENDOR-LEDGER-CREATED | traceId={} | operationVendorId={} | "
                        + "ledgerId={} | ledgerCode={} | ledgerName={} | gstNo={}",
                LOG, traceId, operationVendorId,
                saved.getId(), saved.getLedgerCode(), saved.getLedgerName(), gstNumber);

        return saved;
    }

    private LedgerMaster resolveOrCreateSystemLedger(
            LedgerType ledgerType,
            LedgerGroupType groupType,
            String ledgerName
    ) {
        Optional<LedgerMaster> existing =
                ledgerMasterRepository.findByLedgerTypeAndDeletedFalse(ledgerType);
        if (existing.isPresent() && existing.get().isActive()) {
            return existing.get();
        }

        LedgerGroup group = ledgerGroupRepository
                .findByGroupTypeAndDeletedFalse(groupType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        groupType + " ledger group not found",
                        groupType + "_GROUP_NOT_FOUND"));

        DebitCredit balanceType = DebitCredit.DEBIT; // system purchase/tax/asset ledgers are debit-nature

        LedgerMaster ledger = new LedgerMaster();
        ledger.setLedgerCode(generateSystemLedgerCode(ledgerType));
        ledger.setLedgerName(ledgerName);
        ledger.setLedgerType(ledgerType);
        ledger.setLedgerGroup(group);
        ledger.setOpeningBalance(ZERO);
        ledger.setOpeningBalanceType(balanceType);
        ledger.setCurrentBalance(ZERO);
        ledger.setCurrentBalanceType(balanceType);
        ledger.setSystemCreated(true);
        ledger.setActive(true);
        ledger.setDeleted(false);

        return ledgerMasterRepository.save(ledger);
    }

    // =====================================================================
    // VOUCHER POSTING
    // =====================================================================

    private Long postPurchaseInvoiceVoucher(
            String traceId,
            VendorPaymentApprovalRequestDto s,
            LedgerMaster vendorLedger,
            GstRegistrationType gstType
    ) {
        Long sourceId = s.getProcurementPaymentRequestId();

        // Idempotency: return existing voucher id if already posted
        if (accountingVoucherService.existsPostedVoucher(
                VoucherType.PURCHASE_INVOICE,
                VoucherSourceType.PROCUREMENT_VENDOR_INVOICE,
                sourceId)) {
            Long existingId = findExistingVoucherId(
                    VoucherSourceType.PROCUREMENT_VENDOR_INVOICE, sourceId);
            log.info("{} PURCHASE-INVOICE-EXISTS | traceId={} | sourceId={} | voucherId={}",
                    LOG, traceId, sourceId, existingId);
            return existingId;
        }

        String invoiceNumber = s.getInvoiceNumber() != null
                ? s.getInvoiceNumber()
                : "INV-" + s.getProcurementOrderId() + "-" + sourceId;
        LocalDate invoiceDate = s.getInvoiceDate() != null ? s.getInvoiceDate() : LocalDate.now();

        BigDecimal taxable = money(s.getTaxableAmount());
        BigDecimal cgst = money(s.getCgstAmount());
        BigDecimal sgst = money(s.getSgstAmount());
        BigDecimal igst = money(s.getIgstAmount());

        List<AccountingVoucherEntryRequestDto> entries = new ArrayList<>();

        // DR Purchase (taxable)
        LedgerMaster purchaseLedger = resolveOrCreateSystemLedger(
                LedgerType.PURCHASE, LedgerGroupType.PURCHASE_ACCOUNTS, "Purchases - Procurement");
        entries.add(debit(purchaseLedger.getId(), taxable,
                "Purchase from " + vendorLedger.getLedgerName()));

        // DR Input GST (domestic only)
        if (gstType != GstRegistrationType.INTERNATIONAL && gstType != GstRegistrationType.SEZ) {
            if (cgst.compareTo(BigDecimal.ZERO) > 0) {
                LedgerMaster l = resolveOrCreateSystemLedger(
                        LedgerType.INPUT_CGST, LedgerGroupType.DUTIES_AND_TAXES, "Input CGST");
                entries.add(debit(l.getId(), cgst, "Input CGST on purchase invoice"));
            }
            if (sgst.compareTo(BigDecimal.ZERO) > 0) {
                LedgerMaster l = resolveOrCreateSystemLedger(
                        LedgerType.INPUT_SGST, LedgerGroupType.DUTIES_AND_TAXES, "Input SGST");
                entries.add(debit(l.getId(), sgst, "Input SGST on purchase invoice"));
            }
            if (igst.compareTo(BigDecimal.ZERO) > 0) {
                LedgerMaster l = resolveOrCreateSystemLedger(
                        LedgerType.INPUT_IGST, LedgerGroupType.DUTIES_AND_TAXES, "Input IGST");
                entries.add(debit(l.getId(), igst, "Input IGST on purchase invoice"));
            }
        }

        // CR Vendor = exact sum of debit legs (guarantees balance)
        BigDecimal vendorCredit = sumDebits(entries);
        entries.add(credit(vendorLedger.getId(), vendorCredit,
                "Purchase invoice " + invoiceNumber + " from " + vendorLedger.getLedgerName()));

        AccountingVoucherRequestDto req = AccountingVoucherRequestDto.builder()
                .voucherType(VoucherType.PURCHASE_INVOICE)
                .voucherDate(invoiceDate)
                .sourceType(VoucherSourceType.PROCUREMENT_VENDOR_INVOICE)
                .sourceId(sourceId)
                .narration("Purchase Invoice: " + invoiceNumber
                        + " | Taxable: Rs. " + taxable.toPlainString()
                        + " | GST: Rs. " + cgst.add(sgst).add(igst).toPlainString()
                        + " | Total: Rs. " + vendorCredit.toPlainString())
                .entries(entries)
                .build();

        AccountingVoucherResponseDto voucher = accountingVoucherService.createVoucher(req);

        log.info(
                "{} PURCHASE-INVOICE-POSTED | traceId={} | voucherId={} | voucherNumber={} | "
                        + "invoiceNumber={} | taxable={} | cgst={} | sgst={} | igst={} | vendorCredit={}",
                LOG, traceId, voucher.getId(), voucher.getVoucherNumber(),
                invoiceNumber, taxable, cgst, sgst, igst, vendorCredit);

        return voucher.getId();
    }

    private Long postPaymentVoucher(
            String traceId,
            VendorPaymentApprovalRequestDto s,
            LedgerMaster vendorLedger,
            GstRegistrationType gstType
    ) {
        Long sourceId = s.getProcurementPaymentRequestId();

        if (accountingVoucherService.existsPostedVoucher(
                VoucherType.PAYMENT,
                VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT,
                sourceId)) {
            Long existingId = findExistingVoucherId(
                    VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT, sourceId);
            log.info("{} PAYMENT-VOUCHER-EXISTS | traceId={} | sourceId={} | voucherId={}",
                    LOG, traceId, sourceId, existingId);
            return existingId;
        }

        if (s.getBankLedgerId() == null) {
            throw new ValidationException(
                    "Bank ledger ID is required for payment voucher",
                    "ERR_BANK_LEDGER_ID_REQUIRED", "bankLedgerId");
        }

        LocalDate paymentDate = s.getPaymentDate() != null ? s.getPaymentDate() : LocalDate.now();

        BigDecimal bank = money(s.getBankPaymentAmount());
        BigDecimal tds = money(s.getTdsAmount());

        LedgerMaster bankLedger = ledgerMasterRepository
                .findByIdAndDeletedFalse(s.getBankLedgerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Bank ledger not found: " + s.getBankLedgerId(),
                        "BANK_LEDGER_NOT_FOUND"));

        if (!bankLedger.isActive()) {
            throw new ValidationException(
                    "Bank ledger is inactive: " + bankLedger.getLedgerName(),
                    "ERR_BANK_LEDGER_INACTIVE", "bankLedgerId");
        }
        if (bankLedger.getLedgerType() != LedgerType.BANK
                && bankLedger.getLedgerType() != LedgerType.CASH
                && bankLedger.getLedgerType() != LedgerType.PAYMENT_GATEWAY) {
            throw new ValidationException(
                    "Payment ledger must be BANK, CASH, or PAYMENT_GATEWAY",
                    "ERR_INVALID_PAYMENT_LEDGER", "bankLedgerId");
        }

        List<AccountingVoucherEntryRequestDto> entries = new ArrayList<>();

        // DR Vendor (clear the payable) = bank + tds
        BigDecimal vendorDebit = bank.add(tds).setScale(MONEY_SCALE, ROUNDING);
        entries.add(debit(vendorLedger.getId(), vendorDebit,
                "Payment settled for vendor " + vendorLedger.getLedgerName()
                        + " | Bank: Rs. " + bank.toPlainString()
                        + " | TDS: Rs. " + tds.toPlainString()));

        // CR TDS Payable (domestic only)  -- amount withheld on vendor payment
        if (gstType != GstRegistrationType.INTERNATIONAL && tds.compareTo(BigDecimal.ZERO) > 0) {
            LedgerMaster tdsPayable = resolveOrCreateSystemLedger(
                    LedgerType.TDS_PAYABLE, LedgerGroupType.DUTIES_AND_TAXES, "TDS Payable");
            entries.add(credit(tdsPayable.getId(), tds, "TDS withheld on vendor payment"));
        }

        // CR Bank/Cash (actual outflow)
        entries.add(credit(bankLedger.getId(), bank,
                "Payment to vendor " + vendorLedger.getLedgerName()
                        + " via " + (s.getPaymentMode() != null ? s.getPaymentMode() : "BANK")));

        AccountingVoucherRequestDto req = AccountingVoucherRequestDto.builder()
                .voucherType(VoucherType.PAYMENT)
                .voucherDate(paymentDate)
                .sourceType(VoucherSourceType.PROCUREMENT_VENDOR_PAYMENT)
                .sourceId(sourceId)
                .narration("Vendor Payment | Bank: Rs. " + bank.toPlainString()
                        + " | TDS: Rs. " + tds.toPlainString()
                        + " | Settlement: Rs. " + vendorDebit.toPlainString()
                        + " | Mode: " + s.getPaymentMode()
                        + (s.getTransactionReference() != null
                        ? " | Ref: " + s.getTransactionReference() : ""))
                .entries(entries)
                .build();

        AccountingVoucherResponseDto voucher = accountingVoucherService.createVoucher(req);

        log.info(
                "{} PAYMENT-VOUCHER-POSTED | traceId={} | voucherId={} | voucherNumber={} | "
                        + "vendorLedgerId={} | bankLedgerId={} | bank={} | tds={} | vendorDebit={} | mode={}",
                LOG, traceId, voucher.getId(), voucher.getVoucherNumber(),
                vendorLedger.getId(), bankLedger.getId(), bank, tds, vendorDebit, s.getPaymentMode());

        return voucher.getId();
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private Long findExistingVoucherId(VoucherSourceType sourceType, Long sourceId) {
        return accountingVoucherRepository
                .findFirstBySourceTypeAndSourceIdAndStatusOrderByIdDesc(
                        sourceType, sourceId, VoucherStatus.POSTED)
                .map(AccountingVoucher::getId)
                .orElse(null);
    }

    private AccountingVoucherEntryRequestDto debit(Long ledgerId, BigDecimal amount, String narration) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledgerId)
                .debitAmount(money(amount))
                .creditAmount(ZERO)
                .narration(narration)
                .build();
    }

    private AccountingVoucherEntryRequestDto credit(Long ledgerId, BigDecimal amount, String narration) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledgerId)
                .debitAmount(ZERO)
                .creditAmount(money(amount))
                .narration(narration)
                .build();
    }

    private BigDecimal sumDebits(List<AccountingVoucherEntryRequestDto> entries) {
        return entries.stream()
                .map(AccountingVoucherEntryRequestDto::getDebitAmount)
                .filter(java.util.Objects::nonNull)
                .map(this::money)
                .reduce(ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, ROUNDING);
    }

    private String generateVendorLedgerCode(Long vendorId) {
        return String.format("VEN-%06d", vendorId);
    }

    private String generateSystemLedgerCode(LedgerType type) {
        String prefix = switch (type) {
            case PURCHASE -> "PUR";
            case INPUT_CGST -> "ICGST";
            case INPUT_SGST -> "ISGST";
            case INPUT_IGST -> "IIGST";
            case TDS_PAYABLE -> "TDS-PAY";
            case TDS_RECEIVABLE -> "TDS-REC";
            default -> "SYS";
        };
        String code;
        do {
            code = prefix + "-" + System.nanoTime();
        } while (ledgerMasterRepository.existsByLedgerCodeIgnoreCase(code));
        return code;
    }

    private GstRegistrationType resolveGstRegistrationType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return GstRegistrationType.REGISTERED;
        }
        try {
            return GstRegistrationType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("{} GST-TYPE-INVALID | incomingType={} | defaulting=REGISTERED", LOG, type);
            return GstRegistrationType.REGISTERED;
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(MONEY_SCALE, ROUNDING);
    }
}
