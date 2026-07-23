package com.account.serviceImpl;

import com.account.domain.company.GstRegistrationType;
import com.account.exception.ValidationException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Handles only normal payment-first REGISTERED GST calculations.
 *
 * request.amount / actualBankAmount always means the amount actually
 * credited to Bank, Cash or Payment Gateway.
 *
 * settlementAmount = actualBankAmount + tdsAmount
 */
@Component
public class RegisteredPaymentCalculator {

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2.00");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    private static final String FULL = "FULL";
    private static final String PARTIAL = "PARTIAL";
    private static final String INSTALLMENT = "INSTALLMENT";
    private static final String PURCHASE_ORDER = "PURCHASE_ORDER";

    public Result calculate(Input input) {
        validateInput(input);

        String paymentType = normalizeType(input.getPaymentTypeCode());
        BigDecimal bank = money(input.getActualBankAmount());
        BigDecimal totalTaxable = money(input.getTotalTaxableAmount());
        BigDecimal totalGst = money(input.getTotalGstAmount());
        BigDecimal totalInvoice = money(input.getTotalInvoiceAmount());
        BigDecimal outstanding = money(input.getOutstandingAmount());
        BigDecimal approved = money(input.getApprovedAmount());
        BigDecimal alreadyUsedTds = money(input.getAlreadyUsedTds());

        validateEstimateComposition(totalTaxable, totalGst, totalInvoice);
        validatePoFields(input, paymentType);

        BigDecimal gstPercentage = percentage(totalGst, totalTaxable);

        boolean initialPo = PURCHASE_ORDER.equals(paymentType)
                && bank.compareTo(BigDecimal.ZERO) == 0;

        if (initialPo) {
            return calculateInitialPo(
                    input,
                    paymentType,
                    totalTaxable,
                    totalGst,
                    totalInvoice,
                    outstanding,
                    gstPercentage
            );
        }

        if (bank.compareTo(BigDecimal.ZERO) <= 0) {
            throw fail(
                    "Actual bank amount must be greater than zero",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw fail(
                    "No outstanding amount is available",
                    "ERR_NO_OUTSTANDING_AMOUNT",
                    "amount"
            );
        }

        if (PURCHASE_ORDER.equals(paymentType)
                && !input.isPurchaseOrderProjectCompleted()) {
            throw fail(
                    "Purchase Order payment cannot be registered until "
                            + "the Operation project is COMPLETED",
                    "ERR_PO_PROJECT_NOT_COMPLETED",
                    "paymentTypeId"
            );
        }

        boolean tdsActive = input.isTdsActive();
        BigDecimal tdsPercentage =
                validateAndGetTdsPercentage(
                        tdsActive,
                        input.getTdsPercentage()
                );

        BigDecimal totalAllowedTds = tdsActive
                ? percentOf(totalTaxable, tdsPercentage)
                : ZERO;

        if (tdsActive
                && alreadyUsedTds.compareTo(totalAllowedTds) > 0) {
            throw fail(
                    "Previously registered TDS exceeds total allowed TDS",
                    "ERR_USED_TDS_EXCEEDS_ALLOWED_LIMIT",
                    "tds"
            );
        }

        BigDecimal remainingTdsLimit = tdsActive
                ? totalAllowedTds.subtract(alreadyUsedTds)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
                : ZERO;

        if (FULL.equals(paymentType)) {
            return calculateFull(
                    input,
                    bank,
                    totalTaxable,
                    totalGst,
                    totalInvoice,
                    outstanding,
                    gstPercentage,
                    tdsActive,
                    tdsPercentage,
                    totalAllowedTds,
                    alreadyUsedTds,
                    remainingTdsLimit
            );
        }

        Breakup breakup = tdsActive
                ? breakupFromNetBank(
                bank,
                gstPercentage,
                tdsPercentage
        )
                : breakupWithoutTds(
                bank,
                gstPercentage
        );

        if (tdsActive
                && breakup.tdsAmount.compareTo(remainingTdsLimit) > 0) {
            throw fail(
                    "Calculated TDS exceeds remaining TDS limit. "
                            + "Calculated: ₹" + breakup.tdsAmount
                            + ", remaining: ₹" + remainingTdsLimit,
                    "ERR_CURRENT_TDS_EXCEEDS_REMAINING_LIMIT",
                    "tds"
            );
        }

        validateBreakup(breakup, bank, outstanding);

        validateFlexibleType(
                input,
                paymentType,
                breakup.settlementAmount,
                outstanding,
                approved
        );

        BigDecimal outstandingAfter = outstanding
                .subtract(breakup.settlementAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal remainingTdsAfter = tdsActive
                ? remainingTdsLimit.subtract(breakup.tdsAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
                : ZERO;

        return buildResult(
                input,
                paymentType,
                bank,
                breakup,
                gstPercentage,
                tdsPercentage,
                totalAllowedTds,
                alreadyUsedTds,
                remainingTdsLimit,
                remainingTdsAfter,
                outstanding,
                outstandingAfter,
                false
        );
    }

    private Result calculateFull(
            Input input,
            BigDecimal enteredBank,
            BigDecimal totalTaxable,
            BigDecimal totalGst,
            BigDecimal totalInvoice,
            BigDecimal outstanding,
            BigDecimal gstPercentage,
            boolean tdsActive,
            BigDecimal tdsPercentage,
            BigDecimal totalAllowedTds,
            BigDecimal alreadyUsedTds,
            BigDecimal remainingTdsLimit
    ) {
        /*
         * Outstanding is a GST-inclusive gross amount.
         * Derive its remaining taxable and GST portions first.
         */
        BigDecimal remainingTaxable = outstanding
                .multiply(HUNDRED)
                .divide(
                        HUNDRED.add(gstPercentage),
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal remainingGst = outstanding
                .subtract(remainingTaxable)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentTds = ZERO;

        if (tdsActive) {
            BigDecimal calculatedTds =
                    percentOf(remainingTaxable, tdsPercentage);

            currentTds = calculatedTds
                    .min(remainingTdsLimit)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal expectedBank = outstanding
                .subtract(currentTds)
                .setScale(2, RoundingMode.HALF_UP);

        if (enteredBank.compareTo(expectedBank) != 0) {
            throw fail(
                    "FULL payment bank amount is invalid. Expected: ₹"
                            + expectedBank
                            + ", entered: ₹" + enteredBank
                            + ", TDS: ₹" + currentTds
                            + ", outstanding: ₹" + outstanding,
                    "ERR_FULL_BANK_AMOUNT_MISMATCH",
                    "amount"
            );
        }

        Breakup breakup = new Breakup(
                remainingTaxable,
                remainingGst,
                currentTds,
                outstanding
        );

        validateBreakup(breakup, enteredBank, outstanding);

        return Result.builder()
                .estimateId(input.getEstimateId())
                .unbilledId(input.getUnbilledId())
                .paymentTypeCode(FULL)
                .gstRegistrationType(GstRegistrationType.REGISTERED)
                .tdsActive(tdsActive)
                .actualBankAmount(enteredBank)
                .currentTaxableAmount(remainingTaxable)
                .currentGstAmount(remainingGst)
                .tdsPercentage(tdsPercentage)
                .tdsAmount(currentTds)
                .settlementAmount(outstanding)
                .effectiveGstPercentage(rate(gstPercentage))
                .totalEstimateTaxableAmount(totalTaxable)
                .totalEstimateGstAmount(totalGst)
                .totalEstimateAmount(totalInvoice)
                .totalAllowedTds(totalAllowedTds)
                .alreadyUsedTds(alreadyUsedTds)
                .remainingTdsBefore(remainingTdsLimit)
                .remainingTdsAfter(
                        remainingTdsLimit.subtract(currentTds)
                                .max(BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                )
                .outstandingBefore(outstanding)
                .outstandingAfter(ZERO)
                .initialPurchaseOrder(false)
                .finalSettlement(true)
                .valid(true)
                .build();
    }

    private Result calculateInitialPo(
            Input input,
            String paymentType,
            BigDecimal totalTaxable,
            BigDecimal totalGst,
            BigDecimal totalInvoice,
            BigDecimal outstanding,
            BigDecimal gstPercentage
    ) {
        if (input.isTdsActive()
                || input.getTdsPercentage() != null) {
            throw fail(
                    "TDS cannot be applied during initial zero-value "
                            + "Purchase Order registration",
                    "ERR_TDS_NOT_ALLOWED_ON_INITIAL_PO",
                    "tdsActive"
            );
        }

        return Result.builder()
                .estimateId(input.getEstimateId())
                .unbilledId(input.getUnbilledId())
                .paymentTypeCode(paymentType)
                .gstRegistrationType(GstRegistrationType.REGISTERED)
                .tdsActive(false)
                .actualBankAmount(ZERO)
                .currentTaxableAmount(ZERO)
                .currentGstAmount(ZERO)
                .tdsPercentage(ZERO)
                .tdsAmount(ZERO)
                .settlementAmount(ZERO)
                .effectiveGstPercentage(rate(gstPercentage))
                .totalEstimateTaxableAmount(totalTaxable)
                .totalEstimateGstAmount(totalGst)
                .totalEstimateAmount(totalInvoice)
                .totalAllowedTds(ZERO)
                .alreadyUsedTds(ZERO)
                .remainingTdsBefore(ZERO)
                .remainingTdsAfter(ZERO)
                .outstandingBefore(outstanding)
                .outstandingAfter(outstanding)
                .initialPurchaseOrder(true)
                .finalSettlement(false)
                .valid(true)
                .build();
    }

    private Breakup breakupWithoutTds(
            BigDecimal bank,
            BigDecimal gstPercentage
    ) {
        BigDecimal taxable = bank
                .multiply(HUNDRED)
                .divide(
                        HUNDRED.add(gstPercentage),
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal gst = bank
                .subtract(taxable)
                .setScale(2, RoundingMode.HALF_UP);

        return new Breakup(
                taxable,
                gst,
                ZERO,
                bank
        );
    }

    private Breakup breakupFromNetBank(
            BigDecimal bank,
            BigDecimal gstPercentage,
            BigDecimal tdsPercentage
    ) {
        /*
         * Bank = Taxable + GST - TDS
         * Bank = Taxable × (100 + GST% - TDS%) / 100
         */
        BigDecimal denominator = HUNDRED
                .add(gstPercentage)
                .subtract(tdsPercentage);

        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {
            throw fail(
                    "GST and TDS percentages create an invalid denominator",
                    "ERR_INVALID_REGISTERED_TAX_CALCULATION",
                    "tds"
            );
        }

        BigDecimal rawTaxable = bank
                .multiply(HUNDRED)
                .divide(
                        denominator,
                        10,
                        RoundingMode.HALF_UP
                );

        BigDecimal taxable =
                rawTaxable.setScale(2, RoundingMode.HALF_UP);

        BigDecimal tds = rawTaxable
                .multiply(tdsPercentage)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        BigDecimal settlement = bank
                .add(tds)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal gst = settlement
                .subtract(taxable)
                .setScale(2, RoundingMode.HALF_UP);

        return new Breakup(
                taxable,
                gst,
                tds,
                settlement
        );
    }

    private void validateFlexibleType(
            Input input,
            String paymentType,
            BigDecimal settlement,
            BigDecimal outstanding,
            BigDecimal approved
    ) {
        if (settlement.compareTo(outstanding) > 0) {
            throw fail(
                    "Settlement exceeds outstanding amount",
                    "ERR_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }

        switch (paymentType) {
            case PARTIAL -> {
                /*
                 * First PARTIAL cannot settle the full amount.
                 * A later PARTIAL can settle the final outstanding, avoiding
                 * the PARTIAL -> FULL payment-type lock deadlock.
                 */
                if (settlement.compareTo(outstanding) == 0
                        && approved.compareTo(BigDecimal.ZERO) <= 0) {
                    throw fail(
                            "First PARTIAL payment must be less than outstanding. "
                                    + "Use FULL payment.",
                            "ERR_PARTIAL_SETTLEMENT_MUST_BE_LESS_THAN_OUTSTANDING",
                            "amount"
                    );
                }
            }

            case INSTALLMENT -> {
                BigDecimal eligible =
                        money(input.getInstallmentEligibleAmount());

                if (eligible.compareTo(BigDecimal.ZERO) <= 0) {
                    throw fail(
                            "Installment eligible amount is required",
                            "ERR_INSTALLMENT_ELIGIBLE_AMOUNT_REQUIRED",
                            "installmentId"
                    );
                }

                if (settlement.compareTo(eligible) > 0) {
                    throw fail(
                            "Installment settlement exceeds milestone amount. "
                                    + "Settlement: ₹" + settlement
                                    + ", eligible: ₹" + eligible,
                            "ERR_INSTALLMENT_EXCEEDS_MILESTONE_AMOUNT",
                            "amount"
                    );
                }
            }

            case PURCHASE_ORDER -> {
                // Positive PO payment is already gated by project completion.
            }

            default -> throw fail(
                    "Unsupported REGISTERED payment type: " + paymentType,
                    "ERR_UNSUPPORTED_PAYMENT_TYPE",
                    "paymentTypeId"
            );
        }
    }

    private void validateInput(Input input) {
        if (input == null) {
            throw fail(
                    "Registered payment calculation input is required",
                    "ERR_REGISTERED_PAYMENT_INPUT_REQUIRED",
                    "calculationInput"
            );
        }

        if (input.getGstRegistrationType()
                != GstRegistrationType.REGISTERED) {
            throw fail(
                    "This calculator supports only REGISTERED GST",
                    "ERR_REGISTERED_GST_TYPE_REQUIRED",
                    "gstRegistrationType"
            );
        }

        if (input.getActualBankAmount() == null
                || input.getTotalTaxableAmount() == null
                || input.getTotalGstAmount() == null
                || input.getTotalInvoiceAmount() == null
                || input.getOutstandingAmount() == null) {
            throw fail(
                    "Bank, Estimate and outstanding amounts are required",
                    "ERR_REGISTERED_PAYMENT_AMOUNTS_REQUIRED",
                    "amount"
            );
        }
    }

    private void validateEstimateComposition(
            BigDecimal taxable,
            BigDecimal gst,
            BigDecimal total
    ) {
        taxable = money(taxable);
        gst = money(gst);
        total = money(total);

        if (taxable.compareTo(BigDecimal.ZERO) <= 0
                || gst.compareTo(BigDecimal.ZERO) < 0
                || total.compareTo(BigDecimal.ZERO) <= 0) {

            throw fail(
                    "Estimate taxable/GST/total amounts are invalid",
                    "ERR_ESTIMATE_COMPOSITION_INVALID",
                    "estimateId"
            );
        }

        BigDecimal exactTotal = taxable
                .add(gst)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal roundedHeaderTotal = exactTotal
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        boolean exactMatch =
                total.compareTo(exactTotal) == 0;

        boolean roundedHeaderMatch =
                total.compareTo(roundedHeaderTotal) == 0;

        if (!exactMatch && !roundedHeaderMatch) {
            throw fail(
                    "Estimate taxable amount plus GST does not match total. "
                            + "Taxable: ₹" + taxable
                            + ", GST: ₹" + gst
                            + ", exact total: ₹" + exactTotal
                            + ", rounded total: ₹" + roundedHeaderTotal
                            + ", stored total: ₹" + total,
                    "ERR_ESTIMATE_COMPOSITION_MISMATCH",
                    "estimateId"
            );
        }
    }

    private void validatePoFields(
            Input input,
            String paymentType
    ) {
        if (!PURCHASE_ORDER.equals(paymentType)) {
            return;
        }

        if (blank(input.getPoNumber())) {
            throw fail(
                    "PO number is required",
                    "ERR_PO_NUMBER_REQUIRED",
                    "poNumber"
            );
        }

        if (blank(input.getPoAttachmentUrl())) {
            throw fail(
                    "PO attachment is required",
                    "ERR_PO_ATTACHMENT_REQUIRED",
                    "poAttachmentUrl"
            );
        }

        if (input.getPaymentTermsDays() == null
                || input.getPaymentTermsDays() < 0) {
            throw fail(
                    "Payment terms days is required for Purchase Order",
                    "ERR_PAYMENT_TERMS_DAYS_REQUIRED",
                    "paymentTermsDays"
            );
        }
    }

    private void validateBreakup(
            Breakup breakup,
            BigDecimal bank,
            BigDecimal outstanding
    ) {
        if (breakup.taxableAmount.compareTo(BigDecimal.ZERO) <= 0
                || breakup.gstAmount.compareTo(BigDecimal.ZERO) < 0
                || breakup.tdsAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw fail(
                    "Calculated tax breakup is invalid",
                    "ERR_CALCULATED_TAX_BREAKUP_INVALID",
                    "amount"
            );
        }

        BigDecimal bankPlusTds = bank
                .add(breakup.tdsAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxablePlusGst = breakup.taxableAmount
                .add(breakup.gstAmount)
                .setScale(2, RoundingMode.HALF_UP);

        if (bankPlusTds.compareTo(breakup.settlementAmount) != 0
                || taxablePlusGst.compareTo(breakup.settlementAmount) != 0) {
            throw fail(
                    "Payment breakup does not balance",
                    "ERR_PAYMENT_BREAKUP_NOT_BALANCED",
                    "amount"
            );
        }

        if (breakup.settlementAmount.compareTo(outstanding) > 0) {
            throw fail(
                    "Settlement exceeds outstanding amount",
                    "ERR_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }
    }

    private BigDecimal validateAndGetTdsPercentage(
            boolean active,
            BigDecimal value
    ) {
        if (!active) {
            if (value != null
                    && money(value).compareTo(BigDecimal.ZERO) > 0) {
                throw fail(
                        "TDS percentage must not be supplied when TDS is inactive",
                        "ERR_TDS_NOT_ALLOWED",
                        "tdsPercentage"
                );
            }
            return ZERO;
        }

        if (value == null) {
            throw fail(
                    "TDS percentage is required",
                    "ERR_TDS_PERCENTAGE_REQUIRED",
                    "tdsPercentage"
            );
        }

        BigDecimal percentage = rate(value);

        if (percentage.compareTo(TWO) != 0
                && percentage.compareTo(TEN) != 0) {
            throw fail(
                    "TDS percentage must be 2.00 or 10.00",
                    "ERR_INVALID_TDS_PERCENTAGE",
                    "tdsPercentage"
            );
        }

        return percentage;
    }

    private Result buildResult(
            Input input,
            String paymentType,
            BigDecimal bank,
            Breakup breakup,
            BigDecimal gstPercentage,
            BigDecimal tdsPercentage,
            BigDecimal totalAllowedTds,
            BigDecimal alreadyUsedTds,
            BigDecimal remainingTdsBefore,
            BigDecimal remainingTdsAfter,
            BigDecimal outstandingBefore,
            BigDecimal outstandingAfter,
            boolean initialPo
    ) {
        return Result.builder()
                .estimateId(input.getEstimateId())
                .unbilledId(input.getUnbilledId())
                .paymentTypeCode(paymentType)
                .gstRegistrationType(GstRegistrationType.REGISTERED)
                .tdsActive(input.isTdsActive())
                .actualBankAmount(bank)
                .currentTaxableAmount(breakup.taxableAmount)
                .currentGstAmount(breakup.gstAmount)
                .tdsPercentage(tdsPercentage)
                .tdsAmount(breakup.tdsAmount)
                .settlementAmount(breakup.settlementAmount)
                .effectiveGstPercentage(rate(gstPercentage))
                .totalEstimateTaxableAmount(money(input.getTotalTaxableAmount()))
                .totalEstimateGstAmount(money(input.getTotalGstAmount()))
                .totalEstimateAmount(money(input.getTotalInvoiceAmount()))
                .totalAllowedTds(totalAllowedTds)
                .alreadyUsedTds(alreadyUsedTds)
                .remainingTdsBefore(remainingTdsBefore)
                .remainingTdsAfter(remainingTdsAfter)
                .outstandingBefore(outstandingBefore)
                .outstandingAfter(outstandingAfter)
                .initialPurchaseOrder(initialPo)
                .finalSettlement(
                        !initialPo
                                && outstandingAfter.compareTo(BigDecimal.ZERO) == 0
                )
                .valid(true)
                .build();
    }

    private String normalizeType(String code) {
        if (blank(code)) {
            throw fail(
                    "Payment type code is required",
                    "ERR_PAYMENT_TYPE_CODE_REQUIRED",
                    "paymentTypeId"
            );
        }

        String normalized =
                code.trim().toUpperCase(Locale.ROOT);

        if (!FULL.equals(normalized)
                && !PARTIAL.equals(normalized)
                && !INSTALLMENT.equals(normalized)
                && !PURCHASE_ORDER.equals(normalized)) {
            throw fail(
                    "Unsupported REGISTERED payment type: " + normalized,
                    "ERR_UNSUPPORTED_PAYMENT_TYPE",
                    "paymentTypeId"
            );
        }

        return normalized;
    }

    private BigDecimal percentage(
            BigDecimal part,
            BigDecimal base
    ) {
        return part.multiply(HUNDRED)
                .divide(base, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal percentOf(
            BigDecimal amount,
            BigDecimal percentage
    ) {
        return amount.multiply(percentage)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? ZERO
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal value) {
        return value == null
                ? ZERO
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ValidationException fail(
            String message,
            String code,
            String field
    ) {
        return new ValidationException(message, code, field);
    }

    private record Breakup(
            BigDecimal taxableAmount,
            BigDecimal gstAmount,
            BigDecimal tdsAmount,
            BigDecimal settlementAmount
    ) {
    }

    @Getter
    @Builder
    public static class Input {
        private Long estimateId;
        private Long unbilledId;

        private GstRegistrationType gstRegistrationType;
        private String paymentTypeCode;

        /** Actual Bank/Cash/Payment Gateway receipt. */
        private BigDecimal actualBankAmount;

        private boolean tdsActive;
        private BigDecimal tdsPercentage;

        private BigDecimal totalTaxableAmount;
        private BigDecimal totalGstAmount;
        private BigDecimal totalInvoiceAmount;

        /** Available gross outstanding after subtracting pending receipts. */
        private BigDecimal outstandingAmount;

        /** Accounts-approved settlement already received. */
        private BigDecimal approvedAmount;

        /** PENDING + APPROVED TDS already registered. */
        private BigDecimal alreadyUsedTds;

        /** Gross milestone amount including GST. */
        private BigDecimal installmentEligibleAmount;

        private Integer paymentTermsDays;
        private String poNumber;
        private String poAttachmentUrl;
        private boolean purchaseOrderProjectCompleted;
    }

    @Getter
    @Builder
    public static class Result {
        private Long estimateId;
        private Long unbilledId;

        private String paymentTypeCode;
        private GstRegistrationType gstRegistrationType;

        private boolean tdsActive;
        private BigDecimal actualBankAmount;
        private BigDecimal currentTaxableAmount;
        private BigDecimal currentGstAmount;
        private BigDecimal tdsPercentage;
        private BigDecimal tdsAmount;
        private BigDecimal settlementAmount;

        private BigDecimal effectiveGstPercentage;
        private BigDecimal totalEstimateTaxableAmount;
        private BigDecimal totalEstimateGstAmount;
        private BigDecimal totalEstimateAmount;

        private BigDecimal totalAllowedTds;
        private BigDecimal alreadyUsedTds;
        private BigDecimal remainingTdsBefore;
        private BigDecimal remainingTdsAfter;

        private BigDecimal outstandingBefore;
        private BigDecimal outstandingAfter;

        private boolean initialPurchaseOrder;
        private boolean finalSettlement;
        private boolean valid;
    }
}
