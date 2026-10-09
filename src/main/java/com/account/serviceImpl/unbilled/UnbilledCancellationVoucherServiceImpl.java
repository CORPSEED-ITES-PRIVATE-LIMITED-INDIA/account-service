package com.account.serviceImpl.unbilled;

import com.account.domain.Contact;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.creditNote.CreditNote;
import com.account.domain.creditNote.CreditNoteInvoiceDetail;
import com.account.domain.creditNote.CreditNoteStatus;
import com.account.domain.estimate.Estimate;
import com.account.domain.invoice.Invoice;
import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerMaster;
import com.account.domain.ledger.LedgerType;
import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherType;
import com.account.domain.unbilled.UnbilledInvoice;
import com.account.dto.ledger.AccountingVoucherEntryRequestDto;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.exception.ValidationException;
import com.account.repository.CreditNoteRepository;
import com.account.service.LedgerResolverService;
import com.account.service.UnbilledCancellationVoucherService;
import com.account.service.ledger.AccountingVoucherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Runs when an ADMIN approves an unbilled cancellation request.
 *
 * 1. Creates an APPROVED CreditNote record (visible in the credit notes list)
 * 2. Posts the CREDIT_NOTE accounting voucher to the ledgers
 *
 *   Dr Sales Return / Credit Note      (taxable part)
 *   Dr Output CGST / SGST / IGST       (GST reversal)
 *   Cr Customer ledger                 (received amount)
 *
 * Both run inside the approval transaction (MANDATORY): if either fails, the
 * cancellation is rolled back with it.
 *
 * NOTE: this class must not depend on CreditNoteService. CreditNoteServiceImpl
 * depends on UnbilledService, which depends on this class, so that would be a
 * circular dependency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UnbilledCancellationVoucherServiceImpl implements UnbilledCancellationVoucherService {

    private final AccountingVoucherService accountingVoucherService;
    private final LedgerResolverService ledgerResolverService;
    private final CreditNoteRepository creditNoteRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void postCreditNoteVoucher(UnbilledInvoice unbilled, User admin) {

        if (unbilled == null || unbilled.getId() == null) {
            throw new ValidationException(
                    "Unbilled invoice is required to post credit note voucher",
                    "ERR_UNBILLED_REQUIRED_FOR_CREDIT_VOUCHER"
            );
        }

        // Credit = amount actually received, not the total amount
        BigDecimal credit = money(unbilled.getReceivedAmount());

        if (credit.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Nothing received, credit note and voucher skipped | unbilled={}",
                    unbilled.getUnbilledNumber());
            return;
        }

        if (accountingVoucherService.existsPostedVoucher(
                VoucherType.CREDIT_NOTE,
                VoucherSourceType.UNBILLED_CANCELLATION,
                unbilled.getId()
        )) {
            log.info("Credit note voucher already posted | unbilled={}",
                    unbilled.getUnbilledNumber());
            return;
        }

        // GST split from this unbilled's active tax invoices, else from the estimate
        List<Invoice> invoices =
                unbilled.getTaxInvoices() == null
                        ? List.<Invoice>of()
                        : unbilled.getTaxInvoices().stream()
                        .filter(i -> i != null && !i.isCancelled())
                        .toList();

        BigDecimal base;
        BigDecimal cgstTotal;
        BigDecimal sgstTotal;
        BigDecimal igstTotal;

        if (!invoices.isEmpty()) {
            base = sum(invoices, Invoice::getGrandTotal);
            cgstTotal = sum(invoices, Invoice::getCgstAmount);
            sgstTotal = sum(invoices, Invoice::getSgstAmount);
            igstTotal = sum(invoices, Invoice::getIgstAmount);
        } else {
            Estimate estimate = unbilled.getEstimate();
            base = money(unbilled.getTotalAmount());
            cgstTotal = estimate != null ? money(estimate.getCgstAmount()) : zero();
            sgstTotal = estimate != null ? money(estimate.getSgstAmount()) : zero();
            igstTotal = estimate != null ? money(estimate.getIgstAmount()) : zero();
        }

        if (base.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException(
                    "Invoice total is required for credit note voucher",
                    "ERR_TOTAL_REQUIRED_FOR_CREDIT_VOUCHER"
            );
        }

        BigDecimal ratio = credit
                .divide(base, 10, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);

        BigDecimal cgst = money(cgstTotal.multiply(ratio));
        BigDecimal sgst = money(sgstTotal.multiply(ratio));
        BigDecimal igst = money(igstTotal.multiply(ratio));

        BigDecimal taxable = credit.subtract(cgst).subtract(sgst).subtract(igst);

        if (taxable.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    "GST exceeds credit amount, voucher would not balance",
                    "ERR_CREDIT_VOUCHER_GST_EXCEEDS_AMOUNT"
            );
        }

        // ============================================================
        // 1. CREATE THE APPROVED CREDIT NOTE RECORD
        // ============================================================
        CreditNote creditNote = createApprovedCreditNote(unbilled, admin, credit, invoices);

        // ============================================================
        // 2. POST THE LEDGER VOUCHER
        // ============================================================
        String ref = unbilled.getUnbilledNumber();

        Estimate unbilledEstimate = unbilled.getEstimate();
        String serviceName =
                unbilledEstimate != null
                        && unbilledEstimate.getSolutionName() != null
                        && !unbilledEstimate.getSolutionName().isBlank()
                        ? unbilledEstimate.getSolutionName().trim()
                        : null;
        String serviceSuffix = serviceName != null ? " | Service: " + serviceName : "";
        String cnRef = creditNote.getCreditNoteNumber();

        List<AccountingVoucherEntryRequestDto> entries = new ArrayList<>();

        // Dr Sales Return
        if (taxable.compareTo(BigDecimal.ZERO) > 0) {
            LedgerMaster salesReturn = ledgerResolverService.systemLedger(
                    LedgerType.SALES_RETURN,
                    LedgerGroupType.SALES_ACCOUNTS,
                    "Sales Return / Credit Note",
                    DebitCredit.DEBIT,
                    admin
            );
            entries.add(entry(salesReturn, taxable, zero(),
                    "Sales return booked for credit note " + cnRef + " (" + ref + ")" + serviceSuffix));
        }

        // Dr Output GST (reversal)
        addGst(entries, LedgerType.OUTPUT_CGST, "Output CGST", cgst, admin, cnRef, ref, serviceSuffix);
        addGst(entries, LedgerType.OUTPUT_SGST, "Output SGST", sgst, admin, cnRef, ref, serviceSuffix);
        addGst(entries, LedgerType.OUTPUT_IGST, "Output IGST", igst, admin, cnRef, ref, serviceSuffix);

        // Cr Customer
        LedgerMaster customer = ledgerResolverService.customerLedger(unbilled, admin);
        entries.add(entry(customer, zero(), credit,
                "Credit note " + cnRef + " on cancellation of " + ref + serviceSuffix));

        accountingVoucherService.createVoucher(
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.CREDIT_NOTE)
                        .voucherDate(LocalDate.now())
                        .sourceType(VoucherSourceType.UNBILLED_CANCELLATION)
                        .sourceId(unbilled.getId())
                        .narration("Credit note " + cnRef
                                + " approved on cancellation of " + ref + serviceSuffix)
                        .entries(entries)
                        .build()
        );

        log.info("Cancellation credit note approved and voucher posted | creditNote={} | unbilled={} | service={} | credit={} | taxable={} | cgst={} | sgst={} | igst={}",
                cnRef, ref, serviceName, credit, taxable, cgst, sgst, igst);
    }

    // ================================================================
    // CREDIT NOTE RECORD
    // ================================================================

    private CreditNote createApprovedCreditNote(
            UnbilledInvoice unbilled,
            User admin,
            BigDecimal refundAmount,
            List<Invoice> invoices
    ) {
        Estimate estimate = unbilled.getEstimate();
        Company company = unbilled.getCompany();
        Contact contact = unbilled.getContact();

        LocalDateTime now = LocalDateTime.now();

        // updatedBy still holds the user who raised the cancellation request
        // (cancelUnbilled has not run yet); fall back to the admin.
        User createdBy = unbilled.getUpdatedBy() != null ? unbilled.getUpdatedBy() : admin;

        String attachment = unbilled.getCancelAttachment() != null
                ? unbilled.getCancelAttachment()
                : "";

        String reason = unbilled.getRejectionReason() != null
                && !unbilled.getRejectionReason().isBlank()
                ? unbilled.getRejectionReason().trim()
                : "Unbilled cancellation approved by admin";

        String remarks = "Auto-approved with unbilled cancellation approval";

        CreditNote creditNote = CreditNote.builder()
                .creditNoteNumber(generateCreditNoteNumber())
                .unbilledInvoice(unbilled)
                .estimate(estimate)
                .company(company)
                .contact(contact)
                .unbilledNumber(unbilled.getUnbilledNumber())
                .estimateNumber(estimate != null ? estimate.getEstimateNumber() : null)
                .proposalNumber(null)
                .companyName(company != null ? company.getName() : null)
                .contactName(contact != null ? contact.getName() : null)
                .attachment(attachment)
                .totalAmount(nz(unbilled.getTotalAmount()))
                .receivedAmount(nz(unbilled.getReceivedAmount()))
                .currentReceivedAmount(nz(unbilled.getCurrentReceivedAmount()))
                .outstandingAmount(nz(unbilled.getOutstandingAmount()))
                // whole received amount is credited back, nothing stays as open credit
                .creditAmount(BigDecimal.ZERO)
                .refundAmount(refundAmount)
                .utilizedCreditAmount(BigDecimal.ZERO)
                .remainingCreditAmount(BigDecimal.ZERO)
                .status(CreditNoteStatus.APPROVED)
                .reason(reason)
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)

                // ORGANIZATION / SELLER SNAPSHOT (copied from unbilled)
                .organizationName(unbilled.getOrganizationName())
                .organizationAddressLine1(unbilled.getOrganizationAddressLine1())
                .organizationAddressLine2(unbilled.getOrganizationAddressLine2())
                .organizationCity(unbilled.getOrganizationCity())
                .organizationState(unbilled.getOrganizationState())
                .organizationCountry(unbilled.getOrganizationCountry())
                .organizationPinCode(unbilled.getOrganizationPinCode())
                .organizationGstNo(unbilled.getOrganizationGstNo())
                .organizationPanNo(unbilled.getOrganizationPanNo())
                .organizationCinNumber(unbilled.getOrganizationCinNumber())
                .organizationEmail(unbilled.getOrganizationEmail())
                .organizationPhone(unbilled.getOrganizationPhone())
                .organizationWebsite(unbilled.getOrganizationWebsite())
                .organizationLogoUrl(unbilled.getOrganizationLogoUrl())

                // ORGANIZATION BANK SNAPSHOT (copied from unbilled)
                .organizationBankAccountPresent(unbilled.getOrganizationBankAccountPresent())
                .organizationAccountHolderName(unbilled.getOrganizationAccountHolderName())
                .organizationAccountNumber(unbilled.getOrganizationAccountNumber())
                .organizationIfscCode(unbilled.getOrganizationIfscCode())
                .organizationSwiftCode(unbilled.getOrganizationSwiftCode())
                .organizationBankName(unbilled.getOrganizationBankName())
                .organizationBankBranch(unbilled.getOrganizationBankBranch())
                .organizationUpiId(unbilled.getOrganizationUpiId())
                .organizationPaymentPageLink(unbilled.getOrganizationPaymentPageLink())
                .build();

        // Approval trail (same fields the sales flow fills across its two stages)
        creditNote.setAccountApprovedBy(admin);
        creditNote.setAccountApprovedAt(now);
        creditNote.setAccountApprovalRemarks(remarks);
        creditNote.setApprovedBy(admin);
        creditNote.setApprovedAt(now);
        creditNote.setApprovalRemarks(remarks);

        for (Invoice invoice : invoices) {
            CreditNoteInvoiceDetail detail = CreditNoteInvoiceDetail.builder()
                    .creditNote(creditNote)
                    .invoiceId(invoice.getId())
                    .invoiceNumber(invoice.getInvoiceNumber())
                    .invoiceDate(invoice.getInvoiceDate())
                    .invoiceGrandTotal(invoice.getGrandTotal())
                    .invoiceGstAmount(invoice.getTotalGstAmount())
                    .invoiceCgstAmount(invoice.getCgstAmount())
                    .invoiceSgstAmount(invoice.getSgstAmount())
                    .invoiceIgstAmount(invoice.getIgstAmount())
                    .invoiceStatus(invoice.getStatus() != null ? invoice.getStatus().name() : null)
                    .build();

            creditNote.getInvoiceDetails().add(detail);
        }

        CreditNote saved = creditNoteRepository.save(creditNote);

        log.info("Approved credit note created for cancellation | creditNoteId={} | creditNoteNumber={} | unbilled={}",
                saved.getId(), saved.getCreditNoteNumber(), unbilled.getUnbilledNumber());

        return saved;
    }

    // same numbering as CreditNoteServiceImpl.generateCreditNoteNumber
    private String generateCreditNoteNumber() {

        String dateTimePart = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));

        long count = creditNoteRepository.count() + 1;

        String number = String.format("CN-%s-%06d", dateTimePart, count);

        while (creditNoteRepository.existsByCreditNoteNumber(number)) {
            count++;
            number = String.format("CN-%s-%06d", dateTimePart, count);
        }

        return number;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private void addGst(
            List<AccountingVoucherEntryRequestDto> entries,
            LedgerType type,
            String name,
            BigDecimal amount,
            User admin,
            String cnRef,
            String ref,
            String serviceSuffix
    ) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            LedgerMaster ledger = ledgerResolverService.systemLedger(
                    type,
                    LedgerGroupType.DUTIES_AND_TAXES,
                    name,
                    DebitCredit.CREDIT,
                    admin
            );
            entries.add(entry(ledger, amount, zero(),
                    name + " reversed for credit note " + cnRef + " (" + ref + ")" + serviceSuffix));
        }
    }

    private AccountingVoucherEntryRequestDto entry(
            LedgerMaster ledger,
            BigDecimal debit,
            BigDecimal credit,
            String narration
    ) {
        return AccountingVoucherEntryRequestDto.builder()
                .ledgerId(ledger.getId())
                .debitAmount(debit)
                .creditAmount(credit)
                .narration(narration)
                .build();
    }

    private <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> getter) {
        return list.stream()
                .map(getter)
                .map(this::money)
                .reduce(zero(), BigDecimal::add);
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}