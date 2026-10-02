package com.account.serviceImpl;

import com.account.domain.User;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnbilledCancellationVoucherServiceImpl implements UnbilledCancellationVoucherService {

    private final AccountingVoucherService accountingVoucherService;
    private final LedgerResolverService ledgerResolverService;

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
            log.info("Nothing received, credit note voucher skipped | unbilled={}",
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

        String ref = unbilled.getUnbilledNumber();
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
                    "Sales return on cancellation of " + ref));
        }

        // Dr Output GST (reversal)
        addGst(entries, LedgerType.OUTPUT_CGST, "Output CGST", cgst, admin, ref);
        addGst(entries, LedgerType.OUTPUT_SGST, "Output SGST", sgst, admin, ref);
        addGst(entries, LedgerType.OUTPUT_IGST, "Output IGST", igst, admin, ref);

        // Cr Customer
        LedgerMaster customer = ledgerResolverService.customerLedger(unbilled, admin);
        entries.add(entry(customer, zero(), credit,
                "Credit note on cancellation of " + ref));

        accountingVoucherService.createVoucher(
                AccountingVoucherRequestDto.builder()
                        .voucherType(VoucherType.CREDIT_NOTE)
                        .voucherDate(LocalDate.now())
                        .sourceType(VoucherSourceType.UNBILLED_CANCELLATION)
                        .sourceId(unbilled.getId())
                        .narration("Credit note voucher on cancellation approval: " + ref)
                        .entries(entries)
                        .build()
        );

        log.info("Cancellation credit note voucher posted | unbilled={} | credit={} | taxable={} | cgst={} | sgst={} | igst={}",
                ref, credit, taxable, cgst, sgst, igst);
    }

    private void addGst(
            List<AccountingVoucherEntryRequestDto> entries,
            LedgerType type,
            String name,
            BigDecimal amount,
            User admin,
            String ref
    ) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            LedgerMaster ledger = ledgerResolverService.systemLedger(
                    type,
                    LedgerGroupType.DUTIES_AND_TAXES,
                    name,
                    DebitCredit.CREDIT,
                    admin
            );
            entries.add(entry(ledger, amount, zero(), name + " reversed for " + ref));
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

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}