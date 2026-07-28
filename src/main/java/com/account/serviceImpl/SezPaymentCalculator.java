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
 * Handles only normal payment-first SEZ calculations.
 *
 * Business assumptions:
 * 1. SEZ supply is zero-rated in the Estimate.
 * 2. totalGstAmount must be zero.
 * 3. totalInvoiceAmount must equal totalTaxableAmount.
 * 4. actualBankAmount always means the amount actually credited to
 *    Bank/Cash/Payment Gateway after customer-side TDS deduction.
 *
 * Without TDS:
 *     settlement = bank
 *
 * With TDS:
 *     bank       = taxable - TDS
 *     taxable    = bank / (1 - TDS rate)
 *     settlement = bank + TDS
 */
@Component
public class SezPaymentCalculator {

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final BigDecimal HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal TWO =
            new BigDecimal("2.00");

    private static final BigDecimal TEN =
            new BigDecimal("10.00");


    private static final String FULL = "FULL";
    private static final String PARTIAL = "PARTIAL";
    private static final String INSTALLMENT = "INSTALLMENT";
    private static final String PURCHASE_ORDER = "PURCHASE_ORDER";

    public Result calculate(Input input) {

        validateInput(input);

        String paymentType =
                normalizePaymentType(
                        input.getPaymentTypeCode()
                );

        BigDecimal actualBankAmount =
                money(
                        input.getActualBankAmount()
                );

        BigDecimal totalTaxableAmount =
                money(
                        input.getTotalTaxableAmount()
                );

        BigDecimal totalGstAmount =
                money(
                        input.getTotalGstAmount()
                );

        BigDecimal totalInvoiceAmount =
                money(
                        input.getTotalInvoiceAmount()
                );

        BigDecimal outstandingAmount =
                money(
                        input.getOutstandingAmount()
                );

        BigDecimal approvedAmount =
                money(
                        input.getApprovedAmount()
                );

        BigDecimal alreadyUsedTds =
                money(
                        input.getAlreadyUsedTds()
                );

        validateEstimateComposition(
                totalTaxableAmount,
                totalGstAmount,
                totalInvoiceAmount
        );

        validatePurchaseOrderFields(
                input,
                paymentType
        );

        boolean initialPurchaseOrder =
                PURCHASE_ORDER.equals(paymentType)
                        && actualBankAmount.compareTo(BigDecimal.ZERO) == 0;

        if (initialPurchaseOrder) {
            return calculateInitialPurchaseOrder(
                    input,
                    paymentType,
                    totalTaxableAmount,
                    totalGstAmount,
                    totalInvoiceAmount,
                    outstandingAmount
            );
        }

        if (actualBankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw fail(
                    "Actual Bank/Cash/Payment Gateway amount must be greater than zero",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        if (outstandingAmount.compareTo(BigDecimal.ZERO) <= 0) {
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

        boolean tdsActive =
                input.isTdsActive();

        BigDecimal tdsPercentage =
                validateAndGetTdsPercentage(
                        tdsActive,
                        input.getTdsPercentage()
                );

        BigDecimal totalAllowedTds =
                tdsActive
                        ? percentOf(
                        totalTaxableAmount,
                        tdsPercentage
                )
                        : ZERO;

        if (tdsActive
                && alreadyUsedTds.compareTo(totalAllowedTds) > 0) {

            throw fail(
                    "Previously registered TDS exceeds total allowed TDS",
                    "ERR_USED_TDS_EXCEEDS_ALLOWED_LIMIT",
                    "tds"
            );
        }

        BigDecimal remainingTdsLimit =
                tdsActive
                        ? totalAllowedTds
                        .subtract(alreadyUsedTds)
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                        : ZERO;

        if (tdsActive
                && remainingTdsLimit.compareTo(BigDecimal.ZERO) <= 0) {

            throw fail(
                    "TDS limit is already exhausted",
                    "ERR_TDS_LIMIT_EXHAUSTED",
                    "tds"
            );
        }

        if (FULL.equals(paymentType)) {

            return calculateFullPayment(
                    input,
                    actualBankAmount,
                    totalTaxableAmount,
                    totalGstAmount,
                    totalInvoiceAmount,
                    outstandingAmount,
                    tdsActive,
                    tdsPercentage,
                    totalAllowedTds,
                    alreadyUsedTds,
                    remainingTdsLimit
            );
        }

        Breakup breakup =
                tdsActive
                        ? calculateBreakupWithTds(
                        actualBankAmount,
                        tdsPercentage
                )
                        : calculateBreakupWithoutTds(
                        actualBankAmount
                );

        if (tdsActive
                && breakup.tdsAmount()
                .compareTo(remainingTdsLimit) > 0) {

            throw fail(
                    "Calculated TDS exceeds remaining TDS limit. "
                            + "Calculated: ₹" + breakup.tdsAmount()
                            + ", remaining: ₹" + remainingTdsLimit,
                    "ERR_CURRENT_TDS_EXCEEDS_REMAINING_LIMIT",
                    "tds"
            );
        }

        validateBreakup(
                breakup,
                actualBankAmount,
                outstandingAmount
        );

        validateFlexiblePaymentType(
                input,
                paymentType,
                breakup.settlementAmount(),
                outstandingAmount,
                approvedAmount
        );

        BigDecimal outstandingAfter =
                outstandingAmount
                        .subtract(
                                breakup.settlementAmount()
                        )
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal remainingTdsAfter =
                tdsActive
                        ? remainingTdsLimit
                        .subtract(
                                breakup.tdsAmount()
                        )
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                        : ZERO;

        return buildResult(
                input,
                paymentType,
                actualBankAmount,
                breakup,
                tdsPercentage,
                totalAllowedTds,
                alreadyUsedTds,
                remainingTdsLimit,
                remainingTdsAfter,
                outstandingAmount,
                outstandingAfter,
                false
        );
    }

    private Result calculateFullPayment(
            Input input,
            BigDecimal enteredBankAmount,
            BigDecimal totalTaxableAmount,
            BigDecimal totalGstAmount,
            BigDecimal totalInvoiceAmount,
            BigDecimal outstandingAmount,
            boolean tdsActive,
            BigDecimal tdsPercentage,
            BigDecimal totalAllowedTds,
            BigDecimal alreadyUsedTds,
            BigDecimal remainingTdsLimit
    ) {

        /*
         * For SEZ, outstanding amount itself is the remaining taxable amount,
         * because GST is zero.
         */
        BigDecimal currentTaxableAmount =
                outstandingAmount;

        BigDecimal currentTdsAmount =
                ZERO;

        if (tdsActive) {

            BigDecimal calculatedTds =
                    percentOf(
                            currentTaxableAmount,
                            tdsPercentage
                    );

            currentTdsAmount =
                    calculatedTds
                            .min(remainingTdsLimit)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );
        }

        BigDecimal expectedBankAmount =
                outstandingAmount
                        .subtract(currentTdsAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (enteredBankAmount.compareTo(expectedBankAmount) != 0) {

            throw fail(
                    "FULL SEZ payment Bank amount is invalid. "
                            + "Expected: ₹" + expectedBankAmount
                            + ", entered: ₹" + enteredBankAmount
                            + ", TDS: ₹" + currentTdsAmount
                            + ", outstanding: ₹" + outstandingAmount,
                    "ERR_FULL_BANK_AMOUNT_MISMATCH",
                    "amount"
            );
        }

        Breakup breakup =
                new Breakup(
                        currentTaxableAmount,
                        ZERO,
                        currentTdsAmount,
                        outstandingAmount
                );

        validateBreakup(
                breakup,
                enteredBankAmount,
                outstandingAmount
        );

        return Result.builder()
                .estimateId(
                        input.getEstimateId()
                )
                .unbilledId(
                        input.getUnbilledId()
                )
                .paymentTypeCode(
                        FULL
                )
                .gstRegistrationType(
                        GstRegistrationType.SEZ
                )
                .tdsActive(
                        tdsActive
                )
                .actualBankAmount(
                        enteredBankAmount
                )
                .currentTaxableAmount(
                        currentTaxableAmount
                )
                .currentGstAmount(
                        ZERO
                )
                .tdsPercentage(
                        tdsPercentage
                )
                .tdsAmount(
                        currentTdsAmount
                )
                .settlementAmount(
                        outstandingAmount
                )
                .effectiveGstPercentage(
                        ZERO
                )
                .totalEstimateTaxableAmount(
                        totalTaxableAmount
                )
                .totalEstimateGstAmount(
                        totalGstAmount
                )
                .totalEstimateAmount(
                        totalInvoiceAmount
                )
                .totalAllowedTds(
                        totalAllowedTds
                )
                .alreadyUsedTds(
                        alreadyUsedTds
                )
                .remainingTdsBefore(
                        remainingTdsLimit
                )
                .remainingTdsAfter(
                        remainingTdsLimit
                                .subtract(currentTdsAmount)
                                .max(BigDecimal.ZERO)
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                )
                )
                .outstandingBefore(
                        outstandingAmount
                )
                .outstandingAfter(
                        ZERO
                )
                .initialPurchaseOrder(
                        false
                )
                .finalSettlement(
                        true
                )
                .valid(
                        true
                )
                .build();
    }

    private Result calculateInitialPurchaseOrder(
            Input input,
            String paymentType,
            BigDecimal totalTaxableAmount,
            BigDecimal totalGstAmount,
            BigDecimal totalInvoiceAmount,
            BigDecimal outstandingAmount
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
                .estimateId(
                        input.getEstimateId()
                )
                .unbilledId(
                        input.getUnbilledId()
                )
                .paymentTypeCode(
                        paymentType
                )
                .gstRegistrationType(
                        GstRegistrationType.SEZ
                )
                .tdsActive(
                        false
                )
                .actualBankAmount(
                        ZERO
                )
                .currentTaxableAmount(
                        ZERO
                )
                .currentGstAmount(
                        ZERO
                )
                .tdsPercentage(
                        ZERO
                )
                .tdsAmount(
                        ZERO
                )
                .settlementAmount(
                        ZERO
                )
                .effectiveGstPercentage(
                        ZERO
                )
                .totalEstimateTaxableAmount(
                        totalTaxableAmount
                )
                .totalEstimateGstAmount(
                        totalGstAmount
                )
                .totalEstimateAmount(
                        totalInvoiceAmount
                )
                .totalAllowedTds(
                        ZERO
                )
                .alreadyUsedTds(
                        ZERO
                )
                .remainingTdsBefore(
                        ZERO
                )
                .remainingTdsAfter(
                        ZERO
                )
                .outstandingBefore(
                        outstandingAmount
                )
                .outstandingAfter(
                        outstandingAmount
                )
                .initialPurchaseOrder(
                        true
                )
                .finalSettlement(
                        false
                )
                .valid(
                        true
                )
                .build();
    }

    private Breakup calculateBreakupWithoutTds(
            BigDecimal actualBankAmount
    ) {

        /*
         * SEZ GST = 0.
         *
         * Bank       = Taxable
         * Settlement = Bank
         */
        return new Breakup(
                actualBankAmount,
                ZERO,
                ZERO,
                actualBankAmount
        );
    }

    private Breakup calculateBreakupWithTds(
            BigDecimal actualBankAmount,
            BigDecimal tdsPercentage
    ) {

        /*
         * SEZ:
         *
         * Bank = Taxable - TDS
         *
         * TDS = Taxable × TDS%
         *
         * Bank = Taxable × (100 - TDS%) / 100
         *
         * Taxable = Bank × 100 / (100 - TDS%)
         */
        BigDecimal denominator =
                HUNDRED.subtract(tdsPercentage);

        if (denominator.compareTo(BigDecimal.ZERO) <= 0) {

            throw fail(
                    "TDS percentage creates an invalid SEZ denominator",
                    "ERR_INVALID_SEZ_TDS_CALCULATION",
                    "tds"
            );
        }

        BigDecimal rawTaxableAmount =
                actualBankAmount
                        .multiply(HUNDRED)
                        .divide(
                                denominator,
                                10,
                                RoundingMode.HALF_UP
                        );

        BigDecimal currentTaxableAmount =
                rawTaxableAmount
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        /*
         * Settlement must balance exactly with:
         *
         * Settlement = Bank + TDS
         *
         * Therefore calculate TDS from settlement minus bank after
         * rounding taxable to money scale.
         */
        BigDecimal currentTdsAmount =
                currentTaxableAmount
                        .subtract(actualBankAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal settlementAmount =
                actualBankAmount
                        .add(currentTdsAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        return new Breakup(
                currentTaxableAmount,
                ZERO,
                currentTdsAmount,
                settlementAmount
        );
    }

    private void validateFlexiblePaymentType(
            Input input,
            String paymentType,
            BigDecimal settlementAmount,
            BigDecimal outstandingAmount,
            BigDecimal approvedAmount
    ) {

        if (settlementAmount.compareTo(BigDecimal.ZERO) <= 0) {

            throw fail(
                    "Settlement amount must be greater than zero",
                    "ERR_SETTLEMENT_NOT_POSITIVE",
                    "amount"
            );
        }

        if (settlementAmount.compareTo(outstandingAmount) > 0) {

            throw fail(
                    "Settlement exceeds outstanding amount",
                    "ERR_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }

        switch (paymentType) {

            case PARTIAL -> {

                BigDecimal totalInvoiceAmount =
                        money(input.getTotalInvoiceAmount());

                BigDecimal halfAmount = totalInvoiceAmount.divide(
                        new BigDecimal("2"),
                        2,
                        RoundingMode.HALF_UP
                );

                BigDecimal expectedPartialAmount =
                        halfAmount.min(outstandingAmount);

                if (settlementAmount.compareTo(expectedPartialAmount) != 0) {
                    throw fail(
                            "PARTIAL payment must be exactly ₹"
                                    + expectedPartialAmount
                                    + ". Total Estimate amount: ₹"
                                    + totalInvoiceAmount
                                    + ", outstanding amount: ₹"
                                    + outstandingAmount
                                    + ", current settlement amount: ₹"
                                    + settlementAmount,
                            "ERR_PARTIAL_AMOUNT_MISMATCH",
                            "amount"
                    );
                }
            }

            case INSTALLMENT -> {

                BigDecimal installmentEligibleAmount =
                        money(
                                input.getInstallmentEligibleAmount()
                        );

                if (installmentEligibleAmount
                        .compareTo(BigDecimal.ZERO) <= 0) {

                    throw fail(
                            "Installment eligible amount is required",
                            "ERR_INSTALLMENT_ELIGIBLE_AMOUNT_REQUIRED",
                            "installmentId"
                    );
                }

                if (settlementAmount
                        .compareTo(installmentEligibleAmount) > 0) {

                    throw fail(
                            "Installment settlement exceeds milestone amount. "
                                    + "Settlement: ₹" + settlementAmount
                                    + ", eligible: ₹"
                                    + installmentEligibleAmount,
                            "ERR_INSTALLMENT_EXCEEDS_MILESTONE_AMOUNT",
                            "amount"
                    );
                }
            }

            case PURCHASE_ORDER -> {
                /*
                 * Positive PO payment has already passed project-completion
                 * validation before reaching this method.
                 */
            }

            default -> throw fail(
                    "Unsupported SEZ payment type: " + paymentType,
                    "ERR_UNSUPPORTED_PAYMENT_TYPE",
                    "paymentTypeId"
            );
        }
    }

    private void validateBreakup(
            Breakup breakup,
            BigDecimal actualBankAmount,
            BigDecimal outstandingAmount
    ) {

        if (breakup.taxableAmount()
                .compareTo(BigDecimal.ZERO) <= 0) {

            throw fail(
                    "Calculated taxable amount must be greater than zero",
                    "ERR_CALCULATED_TAXABLE_AMOUNT_INVALID",
                    "amount"
            );
        }

        if (breakup.gstAmount()
                .compareTo(BigDecimal.ZERO) != 0) {

            throw fail(
                    "GST amount must be zero for SEZ",
                    "ERR_SEZ_GST_MUST_BE_ZERO",
                    "gstAmount"
            );
        }

        if (breakup.tdsAmount()
                .compareTo(BigDecimal.ZERO) < 0) {

            throw fail(
                    "Calculated TDS amount cannot be negative",
                    "ERR_CALCULATED_TDS_AMOUNT_INVALID",
                    "tds"
            );
        }

        BigDecimal bankPlusTds =
                actualBankAmount
                        .add(
                                breakup.tdsAmount()
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (bankPlusTds
                .compareTo(
                        breakup.settlementAmount()
                ) != 0) {

            throw fail(
                    "SEZ payment breakup does not balance",
                    "ERR_PAYMENT_BREAKUP_NOT_BALANCED",
                    "amount"
            );
        }

        if (breakup.taxableAmount()
                .compareTo(
                        breakup.settlementAmount()
                ) != 0) {

            throw fail(
                    "SEZ settlement must equal taxable amount because GST is zero",
                    "ERR_SEZ_SETTLEMENT_TAXABLE_MISMATCH",
                    "amount"
            );
        }

        if (breakup.settlementAmount()
                .compareTo(outstandingAmount) > 0) {

            throw fail(
                    "Settlement exceeds outstanding amount",
                    "ERR_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }
    }

    private void validateEstimateComposition(
            BigDecimal totalTaxableAmount,
            BigDecimal totalGstAmount,
            BigDecimal totalInvoiceAmount
    ) {
        totalTaxableAmount = money(totalTaxableAmount);
        totalGstAmount = money(totalGstAmount);
        totalInvoiceAmount = money(totalInvoiceAmount);

        if (totalTaxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw fail(
                    "SEZ taxable amount must be greater than zero",
                    "ERR_SEZ_TAXABLE_AMOUNT_REQUIRED",
                    "estimateId"
            );
        }

        /*
         * SEZ is zero-rated. GST must always remain zero.
         */
        if (totalGstAmount.compareTo(BigDecimal.ZERO) != 0) {
            throw fail(
                    "SEZ Estimate GST amount must be zero. Found: ₹"
                            + totalGstAmount,
                    "ERR_SEZ_ESTIMATE_GST_NOT_ZERO",
                    "estimateId"
            );
        }

        if (totalInvoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw fail(
                    "SEZ Estimate total amount must be greater than zero",
                    "ERR_SEZ_TOTAL_AMOUNT_REQUIRED",
                    "estimateId"
            );
        }

        /*
         * Without header rounding:
         * total = taxable because GST is zero.
         */
        BigDecimal exactTotal = totalTaxableAmount
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * With Estimate header rounding:
         * taxable = 49993.40
         * total   = 49993.00
         */
        BigDecimal roundedHeaderTotal = exactTotal
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        boolean exactMatch =
                totalInvoiceAmount.compareTo(exactTotal) == 0;

        boolean roundedHeaderMatch =
                totalInvoiceAmount.compareTo(roundedHeaderTotal) == 0;

        if (!exactMatch && !roundedHeaderMatch) {
            throw fail(
                    "Invalid SEZ Estimate composition. "
                            + "Taxable: ₹" + totalTaxableAmount
                            + ", GST: ₹" + totalGstAmount
                            + ", exact total: ₹" + exactTotal
                            + ", rounded total: ₹" + roundedHeaderTotal
                            + ", stored total: ₹" + totalInvoiceAmount,
                    "ERR_SEZ_ESTIMATE_COMPOSITION_MISMATCH",
                    "estimateId"
            );
        }
    }

    private void validatePurchaseOrderFields(
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

    private void validateInput(
            Input input
    ) {

        if (input == null) {

            throw fail(
                    "SEZ payment calculation input is required",
                    "ERR_SEZ_PAYMENT_INPUT_REQUIRED",
                    "calculationInput"
            );
        }

        if (input.getGstRegistrationType()
                != GstRegistrationType.SEZ) {

            throw fail(
                    "This calculator supports only SEZ GST type",
                    "ERR_SEZ_GST_TYPE_REQUIRED",
                    "gstRegistrationType"
            );
        }
    }

    private BigDecimal validateAndGetTdsPercentage(
            boolean tdsActive,
            BigDecimal value
    ) {

        if (!tdsActive) {

            if (value != null
                    && money(value)
                    .compareTo(BigDecimal.ZERO) > 0) {

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

        BigDecimal percentage =
                rate(value);

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
            BigDecimal actualBankAmount,
            Breakup breakup,
            BigDecimal tdsPercentage,
            BigDecimal totalAllowedTds,
            BigDecimal alreadyUsedTds,
            BigDecimal remainingTdsBefore,
            BigDecimal remainingTdsAfter,
            BigDecimal outstandingBefore,
            BigDecimal outstandingAfter,
            boolean initialPurchaseOrder
    ) {

        return Result.builder()
                .estimateId(
                        input.getEstimateId()
                )
                .unbilledId(
                        input.getUnbilledId()
                )
                .paymentTypeCode(
                        paymentType
                )
                .gstRegistrationType(
                        GstRegistrationType.SEZ
                )
                .tdsActive(
                        input.isTdsActive()
                )
                .actualBankAmount(
                        actualBankAmount
                )
                .currentTaxableAmount(
                        breakup.taxableAmount()
                )
                .currentGstAmount(
                        ZERO
                )
                .tdsPercentage(
                        tdsPercentage
                )
                .tdsAmount(
                        breakup.tdsAmount()
                )
                .settlementAmount(
                        breakup.settlementAmount()
                )
                .effectiveGstPercentage(
                        ZERO
                )
                .totalEstimateTaxableAmount(
                        money(
                                input.getTotalTaxableAmount()
                        )
                )
                .totalEstimateGstAmount(
                        ZERO
                )
                .totalEstimateAmount(
                        money(
                                input.getTotalInvoiceAmount()
                        )
                )
                .totalAllowedTds(
                        totalAllowedTds
                )
                .alreadyUsedTds(
                        alreadyUsedTds
                )
                .remainingTdsBefore(
                        remainingTdsBefore
                )
                .remainingTdsAfter(
                        remainingTdsAfter
                )
                .outstandingBefore(
                        outstandingBefore
                )
                .outstandingAfter(
                        outstandingAfter
                )
                .initialPurchaseOrder(
                        initialPurchaseOrder
                )
                .finalSettlement(
                        !initialPurchaseOrder
                                && outstandingAfter
                                .compareTo(BigDecimal.ZERO) == 0
                )
                .valid(
                        true
                )
                .build();
    }

    private String normalizePaymentType(
            String paymentTypeCode
    ) {

        if (blank(paymentTypeCode)) {

            throw fail(
                    "Payment type code is required",
                    "ERR_PAYMENT_TYPE_CODE_REQUIRED",
                    "paymentTypeId"
            );
        }

        String normalized =
                paymentTypeCode
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (!FULL.equals(normalized)
                && !PARTIAL.equals(normalized)
                && !INSTALLMENT.equals(normalized)
                && !PURCHASE_ORDER.equals(normalized)) {

            throw fail(
                    "Unsupported SEZ payment type: "
                            + normalized,
                    "ERR_UNSUPPORTED_PAYMENT_TYPE",
                    "paymentTypeId"
            );
        }

        return normalized;
    }

    private BigDecimal percentOf(
            BigDecimal amount,
            BigDecimal percentage
    ) {

        return amount
                .multiply(percentage)
                .divide(
                        HUNDRED,
                        2,
                        RoundingMode.HALF_UP
                );
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

    private BigDecimal rate(
            BigDecimal value
    ) {

        return value == null
                ? ZERO
                : value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private boolean blank(
            String value
    ) {

        return value == null
                || value.trim().isEmpty();
    }

    private ValidationException fail(
            String message,
            String code,
            String field
    ) {

        return new ValidationException(
                message,
                code,
                field
        );
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

        /**
         * Actual amount credited to Bank/Cash/Payment Gateway.
         */
        private BigDecimal actualBankAmount;

        private boolean tdsActive;
        private BigDecimal tdsPercentage;

        /**
         * SEZ taxable amount. GST must be zero.
         */
        private BigDecimal totalTaxableAmount;

        /**
         * Must be zero for SEZ.
         */
        private BigDecimal totalGstAmount;

        /**
         * Must equal totalTaxableAmount for SEZ.
         */
        private BigDecimal totalInvoiceAmount;

        /**
         * Available settlement after subtracting pending receipts.
         */
        private BigDecimal outstandingAmount;

        /**
         * Accounts-approved settlement already received.
         */
        private BigDecimal approvedAmount;

        /**
         * PENDING + APPROVED TDS already registered.
         */
        private BigDecimal alreadyUsedTds;

        /**
         * Gross milestone amount. For SEZ, this is also taxable amount,
         * because GST is zero.
         */
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

        /**
         * Actual money received in Bank/Cash/Gateway.
         */
        private BigDecimal actualBankAmount;

        /**
         * Current SEZ taxable settlement.
         */
        private BigDecimal currentTaxableAmount;

        /**
         * Always zero for SEZ.
         */
        private BigDecimal currentGstAmount;

        private BigDecimal tdsPercentage;
        private BigDecimal tdsAmount;

        /**
         * Bank + TDS. Also equal to taxable amount for SEZ.
         */
        private BigDecimal settlementAmount;

        /**
         * Always zero for SEZ.
         */
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