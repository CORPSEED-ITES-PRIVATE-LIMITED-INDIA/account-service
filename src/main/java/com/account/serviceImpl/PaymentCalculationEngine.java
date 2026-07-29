package com.account.serviceImpl;

import com.account.domain.company.GstRegistrationType;
import com.account.exception.ValidationException;
import lombok.Builder;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Component
public class PaymentCalculationEngine {

    private static final Logger log =
            LogManager.getLogger(PaymentCalculationEngine.class);

    private static final BigDecimal ZERO =
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private static final BigDecimal HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal TDS_TWO =
            new BigDecimal("2.00");

    private static final BigDecimal TDS_TEN =
            new BigDecimal("10.00");


    /**
     * Central calculation entry point.
     *
     * request.bankAmount means only the amount actually received in:
     * - Bank
     * - Cash
     * - Payment Gateway
     *
     * Settlement amount:
     *
     * Bank amount + TDS amount
     *
     * Example: Bank 900 + TDS 100 = Settlement 1,000
     */
    public PaymentCalculationResult calculate(
            PaymentCalculationRequest request
    ) {

        if (request == null) {
            throw new ValidationException(
                    "Payment calculation request is required",
                    "ERR_PAYMENT_CALCULATION_REQUEST_REQUIRED",
                    "calculationRequest"
            );
        }

        String traceId = resolveTraceId(request.getTraceId());

        log.info(
                "[PAYMENT-CALC-RAW-REQUEST] traceId={} | "
                        + "estimateId={} | unbilledId={} | invoiceId={} | "
                        + "incomingGstRegistrationType={} | "
                        + "incomingPaymentTypeCode={} | incomingBankAmount={} | "
                        + "incomingTdsActive={} | incomingTdsPercentage={} | "
                        + "incomingTotalTaxableAmount={} | incomingTotalGstAmount={} | "
                        + "incomingTotalInvoiceAmount={} | incomingOutstandingAmount={} | "
                        + "incomingAlreadyUsedTds={} | incomingPaymentTermsDays={} | "
                        + "incomingInstallmentEligibleAmount={}",
                traceId,
                request.getEstimateId(),
                request.getUnbilledId(),
                request.getInvoiceId(),
                request.getGstRegistrationType(),
                request.getPaymentTypeCode(),
                request.getBankAmount(),
                request.getTdsActive(),
                request.getTdsPercentage(),
                request.getTotalTaxableAmount(),
                request.getTotalGstAmount(),
                request.getTotalInvoiceAmount(),
                request.getOutstandingAmount(),
                request.getAlreadyUsedTds(),
                request.getPaymentTermsDays(),
                request.getInstallmentEligibleAmount()
        );

        GstRegistrationType gstRegistrationType =
                request.getGstRegistrationType() != null
                        ? request.getGstRegistrationType()
                        : GstRegistrationType.REGISTERED;

        if (request.getGstRegistrationType() == null) {
            log.warn(
                    "[PAYMENT-GST-TYPE-RESOLVED] traceId={} | "
                            + "estimateId={} | unbilledId={} | invoiceId={} | "
                            + "source=DEFAULT | incomingGstType=null | resolvedGstType={} | "
                            + "defaultReason=request GST registration type was null",
                    traceId,
                    request.getEstimateId(),
                    request.getUnbilledId(),
                    request.getInvoiceId(),
                    gstRegistrationType
            );
        } else {
            log.info(
                    "[PAYMENT-GST-TYPE-RESOLVED] traceId={} | "
                            + "estimateId={} | unbilledId={} | invoiceId={} | "
                            + "source=REQUEST | incomingGstType={} | resolvedGstType={}",
                    traceId,
                    request.getEstimateId(),
                    request.getUnbilledId(),
                    request.getInvoiceId(),
                    request.getGstRegistrationType(),
                    gstRegistrationType
            );
        }

        String paymentTypeCode =
                normalizePaymentType(request.getPaymentTypeCode());

        BigDecimal bankAmount =
                money(request.getBankAmount());

        BigDecimal totalTaxableAmount =
                money(request.getTotalTaxableAmount());

        BigDecimal totalGstAmount =
                resolveTotalGstAmount(
                        request,
                        totalTaxableAmount
                );

        BigDecimal totalInvoiceAmount =
                money(request.getTotalInvoiceAmount());

        BigDecimal outstandingAmount =
                money(request.getOutstandingAmount());

        BigDecimal alreadyUsedTds =
                money(request.getAlreadyUsedTds());

        BigDecimal tdsPercentage =
                rate(request.getTdsPercentage());

        boolean tdsActive =
                Boolean.TRUE.equals(request.getTdsActive());

        log.info(
                "[PAYMENT-CALC-NORMALIZED-VALUES] traceId={} | "
                        + "gstType={} | paymentType={} | bankAmount={} | "
                        + "totalTaxableAmount={} | totalGstAmount={} | "
                        + "totalInvoiceAmount={} | outstandingAmount={} | "
                        + "alreadyUsedTds={} | tdsActive={} | tdsPercentage={}",
                traceId,
                gstRegistrationType,
                paymentTypeCode,
                bankAmount,
                totalTaxableAmount,
                totalGstAmount,
                totalInvoiceAmount,
                outstandingAmount,
                alreadyUsedTds,
                tdsActive,
                tdsPercentage
        );

        String scenario =
                buildScenario(
                        gstRegistrationType,
                        paymentTypeCode,
                        tdsActive,
                        bankAmount
                );

        log.info(
                "[PAYMENT-CALC-START] traceId={} | scenario={} | "
                        + "estimateId={} | unbilledId={} | invoiceId={} | "
                        + "gstType={} | paymentType={} | bankAmount={} | "
                        + "tdsActive={} | tdsPercentage={} | "
                        + "totalTaxable={} | totalGst={} | "
                        + "totalInvoice={} | outstanding={} | "
                        + "alreadyUsedTds={} | paymentTermsDays={} | "
                        + "installmentEligibleAmount={}",
                traceId,
                scenario,
                request.getEstimateId(),
                request.getUnbilledId(),
                request.getInvoiceId(),
                gstRegistrationType,
                paymentTypeCode,
                bankAmount,
                tdsActive,
                tdsPercentage,
                totalTaxableAmount,
                totalGstAmount,
                totalInvoiceAmount,
                outstandingAmount,
                alreadyUsedTds,
                request.getPaymentTermsDays(),
                money(request.getInstallmentEligibleAmount())
        );

        validateSupportedPaymentType(
                traceId,
                paymentTypeCode
        );

        boolean initialPurchaseOrder =
                "PURCHASE_ORDER".equals(paymentTypeCode)
                        && bankAmount.compareTo(BigDecimal.ZERO) == 0;

        /*
         * Initial zero-value Purchase Order:
         *
         * - No bank receipt
         * - No TDS
         * - No settlement
         * - No invoice
         */
        if (initialPurchaseOrder) {
            return calculateInitialPurchaseOrder(
                    traceId,
                    scenario,
                    request,
                    gstRegistrationType,
                    paymentTypeCode
            );
        }

        validateBasicAmounts(
                traceId,
                bankAmount,
                totalTaxableAmount,
                totalInvoiceAmount,
                outstandingAmount
        );

        validateInvoiceComposition(
                traceId,
                gstRegistrationType,
                totalTaxableAmount,
                totalGstAmount,
                totalInvoiceAmount
        );

        validateInternationalRules(
                traceId,
                gstRegistrationType,
                tdsActive,
                request.getTdsPercentage()
        );

        BigDecimal effectiveGstPercentage =
                calculateEffectiveGstPercentage(
                        traceId,
                        gstRegistrationType,
                        totalTaxableAmount,
                        totalGstAmount
                );

        log.info(
                "[PAYMENT-GST-RULE] traceId={} | scenario={} | "
                        + "gstType={} | zeroRated={} | "
                        + "totalTaxable={} | totalGst={} | "
                        + "effectiveGstPercentage={}",
                traceId,
                scenario,
                gstRegistrationType,
                isZeroRated(gstRegistrationType),
                totalTaxableAmount,
                totalGstAmount,
                effectiveGstPercentage
        );

        CalculationValues values;

        if (!tdsActive) {

            log.info(
                    "[PAYMENT-CALC-BRANCH] traceId={} | scenario={} | "
                            + "selectedBranch=WITHOUT_TDS | gstType={} | paymentType={}",
                    traceId,
                    scenario,
                    gstRegistrationType,
                    paymentTypeCode
            );

            values = calculateWithoutTds(
                    traceId,
                    scenario,
                    gstRegistrationType,
                    bankAmount,
                    totalTaxableAmount,
                    totalInvoiceAmount
            );

        } else {

            log.info(
                    "[PAYMENT-CALC-BRANCH] traceId={} | scenario={} | "
                            + "selectedBranch=WITH_TDS | gstType={} | paymentType={} | "
                            + "tdsPercentage={}",
                    traceId,
                    scenario,
                    gstRegistrationType,
                    paymentTypeCode,
                    tdsPercentage
            );

            validateTdsPercentage(
                    traceId,
                    tdsPercentage
            );

            values = calculateWithTds(
                    traceId,
                    scenario,
                    paymentTypeCode,
                    gstRegistrationType,
                    bankAmount,
                    tdsPercentage,
                    effectiveGstPercentage,
                    totalTaxableAmount,
                    outstandingAmount,
                    alreadyUsedTds
            );
        }

        validateSettlementAgainstOutstanding(
                traceId,
                values.settlementAmount,
                outstandingAmount
        );

        validatePaymentTypeRules(
                traceId,
                paymentTypeCode,
                values.settlementAmount,
                totalInvoiceAmount,
                outstandingAmount,
                request.getPaymentTermsDays(),
                money(request.getInstallmentEligibleAmount())
        );

        BigDecimal outstandingAfterPayment =
                outstandingAmount
                        .subtract(values.settlementAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        PaymentCalculationResult result =
                PaymentCalculationResult.builder()
                        .traceId(traceId)
                        .scenario(scenario)
                        .gstRegistrationType(gstRegistrationType)
                        .paymentTypeCode(paymentTypeCode)
                        .tdsActive(tdsActive)

                        .enteredTaxableAmount(
                                money(values.currentTaxableAmount)
                        )

                        .actualBankAmount(
                                money(values.actualBankAmount)
                        )

                        .currentTaxableAmount(
                                money(values.currentTaxableAmount)
                        )

                        .currentGstAmount(
                                money(values.currentGstAmount)
                        )

                        .tdsPercentage(
                                tdsPercentage
                        )

                        .tdsAmount(
                                money(values.tdsAmount)
                        )

                        .settlementAmount(
                                money(values.settlementAmount)
                        )

                        .effectiveGstPercentage(
                                rate(effectiveGstPercentage)
                        )

                        .totalAllowedTds(
                                money(values.totalAllowedTds)
                        )

                        .alreadyUsedTds(
                                alreadyUsedTds
                        )

                        .remainingTdsLimit(
                                money(values.remainingTdsLimit)
                        )

                        .maximumBankReceivable(
                                money(values.maximumBankReceivable)
                        )

                        .outstandingBefore(
                                outstandingAmount
                        )

                        .outstandingAfter(
                                outstandingAfterPayment
                        )

                        .initialPurchaseOrder(false)
                        .valid(true)
                        .build();

        log.info(
                "[PAYMENT-CALC-SUCCESS] traceId={} | scenario={} | "
                        + "enteredTaxableAmount={} | actualBankAmount={} | "
                        + "gstAmount={} | tdsPercentage={} | tdsAmount={} | "
                        + "settlementAmount={} | outstandingBefore={} | "
                        + "outstandingAfter={} | totalAllowedTds={} | "
                        + "alreadyUsedTds={} | remainingTdsLimit={} | "
                        + "maximumBankReceivable={}",
                traceId,
                scenario,
                result.getEnteredTaxableAmount(),
                result.getActualBankAmount(),
                result.getCurrentGstAmount(),
                result.getTdsPercentage(),
                result.getTdsAmount(),
                result.getSettlementAmount(),
                result.getOutstandingBefore(),
                result.getOutstandingAfter(),
                result.getTotalAllowedTds(),
                result.getAlreadyUsedTds(),
                result.getRemainingTdsLimit(),
                result.getMaximumBankReceivable()
        );

        return result;
    }

    private PaymentCalculationResult calculateInitialPurchaseOrder(
            String traceId,
            String scenario,
            PaymentCalculationRequest request,
            GstRegistrationType gstRegistrationType,
            String paymentTypeCode
    ) {

        if (Boolean.TRUE.equals(request.getTdsActive())
                || request.getTdsPercentage() != null) {

            throw validationFailure(
                    traceId,
                    "INITIAL_PO_TDS_BLOCKED",
                    "TDS cannot be applied during initial zero-value Purchase Order registration",
                    "ERR_TDS_NOT_ALLOWED_ON_INITIAL_PO",
                    "tdsActive"
            );
        }

        if (request.getPaymentTermsDays() == null
                || request.getPaymentTermsDays() < 0) {

            throw validationFailure(
                    traceId,
                    "INITIAL_PO_TERMS_MISSING",
                    "Payment terms days is required for Purchase Order payment type",
                    "ERR_PAYMENT_TERMS_DAYS_REQUIRED",
                    "paymentTermsDays"
            );
        }

        PaymentCalculationResult result =
                PaymentCalculationResult.builder()
                        .traceId(traceId)
                        .scenario(scenario)
                        .gstRegistrationType(gstRegistrationType)
                        .paymentTypeCode(paymentTypeCode)
                        .tdsActive(false)

                        .bankAmount(ZERO)
                        .currentTaxableAmount(ZERO)
                        .currentGstAmount(ZERO)
                        .tdsPercentage(ZERO)
                        .tdsAmount(ZERO)
                        .settlementAmount(ZERO)

                        .effectiveGstPercentage(ZERO)
                        .totalAllowedTds(ZERO)
                        .alreadyUsedTds(ZERO)
                        .remainingTdsLimit(ZERO)
                        .maximumBankReceivable(ZERO)

                        .outstandingBefore(
                                money(request.getOutstandingAmount())
                        )
                        .outstandingAfter(
                                money(request.getOutstandingAmount())
                        )

                        .initialPurchaseOrder(true)
                        .valid(true)
                        .build();

        log.info(
                "[PAYMENT-CALC-SUCCESS] traceId={} | scenario={} | "
                        + "initialPurchaseOrder=true | bankAmount=0.00 | "
                        + "tdsAmount=0.00 | settlementAmount=0.00 | "
                        + "paymentTermsDays={}",
                traceId,
                scenario,
                request.getPaymentTermsDays()
        );

        return result;
    }

    private CalculationValues calculateWithoutTds(
            String traceId,
            String scenario,
            GstRegistrationType gstRegistrationType,
            BigDecimal enteredBankAmount,
            BigDecimal totalTaxableAmount,
            BigDecimal totalInvoiceAmount
    ) {

        /*
         * Business rule:
         * request.bankAmount is always the actual amount received from the customer.
         * When TDS is inactive, settlement equals the actual bank receipt.
         */
        BigDecimal actualBankAmount = money(enteredBankAmount);

        if (actualBankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "BANK_AMOUNT_NOT_POSITIVE",
                    "Actual received amount must be greater than zero",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        BigDecimal settlementAmount = actualBankAmount;

        /*
         * These values are informational only in the no-TDS branch.
         * The payment registration amount is not grossed up and GST is not added again.
         */
        BigDecimal currentTaxableAmount = settlementAmount;
        BigDecimal currentGstAmount = ZERO;

        log.info(
                "[PAYMENT-NO-TDS-CALCULATION] traceId={} | scenario={} | "
                        + "inputMeaning=ACTUAL_BANK_AMOUNT | gstType={} | "
                        + "actualBankAmount={} | tdsAmount=0.00 | settlementAmount={}",
                traceId,
                scenario,
                gstRegistrationType,
                actualBankAmount,
                settlementAmount
        );

        return new CalculationValues(
                currentTaxableAmount,
                currentGstAmount,
                ZERO,
                actualBankAmount,
                settlementAmount,
                ZERO,
                ZERO,
                ZERO
        );
    }

    private CalculationValues calculateWithTds(
            String traceId,
            String scenario,
            String paymentTypeCode,
            GstRegistrationType gstRegistrationType,
            BigDecimal enteredBankAmount,
            BigDecimal tdsPercentage,
            BigDecimal effectiveGstPercentage,
            BigDecimal totalTaxableAmount,
            BigDecimal outstandingAmount,
            BigDecimal alreadyUsedTds
    ) {

        BigDecimal actualBankAmount =
                money(enteredBankAmount);

        BigDecimal safeTdsPercentage =
                rate(tdsPercentage);

        BigDecimal safeTotalTaxableAmount =
                money(totalTaxableAmount);

        BigDecimal safeOutstandingAmount =
                money(outstandingAmount);

        BigDecimal safeAlreadyUsedTds =
                money(alreadyUsedTds);

        if (actualBankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "BANK_AMOUNT_NOT_POSITIVE",
                    "Actual received amount must be greater than zero",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        BigDecimal totalAllowedTds =
                safeTotalTaxableAmount
                        .multiply(safeTdsPercentage)
                        .divide(
                                HUNDRED,
                                2,
                                RoundingMode.HALF_UP
                        );

        if (safeAlreadyUsedTds.compareTo(totalAllowedTds) > 0) {
            throw validationFailure(
                    traceId,
                    "USED_TDS_EXCEEDS_LIMIT",
                    "Previously registered TDS exceeds total allowed TDS",
                    "ERR_USED_TDS_EXCEEDS_ALLOWED_LIMIT",
                    "tds"
            );
        }

        BigDecimal remainingTdsLimit =
                totalAllowedTds
                        .subtract(safeAlreadyUsedTds)
                        .max(BigDecimal.ZERO)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (remainingTdsLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "TDS_LIMIT_EXHAUSTED",
                    "TDS limit is already exhausted",
                    "ERR_TDS_LIMIT_EXHAUSTED",
                    "tds"
            );
        }

        /*
         * Business rule:
         *
         * Salesperson enters actual amount credited to Bank.
         *
         * Gross settlement = Bank / (1 - TDS rate)
         *
         * Example:
         * Bank = 900
         * TDS  = 10%
         *
         * Settlement = 900 / 0.90 = 1000
         * TDS        = 1000 - 900 = 100
         */
        BigDecimal tdsRateFraction =
                safeTdsPercentage.divide(
                        HUNDRED,
                        8,
                        RoundingMode.HALF_UP
                );

        BigDecimal netFactor =
                BigDecimal.ONE.subtract(tdsRateFraction);

        if (netFactor.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "INVALID_TDS_NET_FACTOR",
                    "Unable to calculate TDS from received amount",
                    "ERR_INVALID_TDS_CALCULATION",
                    "tds.tdsPercentage"
            );
        }

        BigDecimal settlementAmount =
                actualBankAmount
                        .divide(
                                netFactor,
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal currentTdsAmount =
                settlementAmount
                        .subtract(actualBankAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        if (currentTdsAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "CURRENT_TDS_INVALID",
                    "Calculated TDS amount must be greater than zero",
                    "ERR_TDS_AMOUNT_INVALID",
                    "tds"
            );
        }

        if (currentTdsAmount.compareTo(remainingTdsLimit) > 0) {
            throw validationFailure(
                    traceId,
                    "CURRENT_TDS_EXCEEDS_REMAINING_LIMIT",
                    "Calculated TDS exceeds remaining TDS limit. "
                            + "Calculated TDS: ₹" + currentTdsAmount
                            + ", remaining TDS limit: ₹" + remainingTdsLimit,
                    "ERR_CURRENT_TDS_EXCEEDS_REMAINING_LIMIT",
                    "tds"
            );
        }

        if (settlementAmount.compareTo(safeOutstandingAmount) > 0) {
            throw validationFailure(
                    traceId,
                    "SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "Bank amount plus TDS exceeds outstanding amount. "
                            + "Bank amount: ₹" + actualBankAmount
                            + ", TDS amount: ₹" + currentTdsAmount
                            + ", settlement amount: ₹" + settlementAmount
                            + ", outstanding amount: ₹" + safeOutstandingAmount,
                    "ERR_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }

        if ("FULL".equals(paymentTypeCode)
                && settlementAmount.compareTo(safeOutstandingAmount) != 0) {

            throw validationFailure(
                    traceId,
                    "FULL_SETTLEMENT_MISMATCH",
                    "For FULL payment, Bank amount plus TDS must equal outstanding amount. "
                            + "Settlement: ₹" + settlementAmount
                            + ", outstanding: ₹" + safeOutstandingAmount,
                    "ERR_FULL_AMOUNT_MISMATCH",
                    "amount"
            );
        }

        if ("PARTIAL".equals(paymentTypeCode)
                && settlementAmount.compareTo(safeOutstandingAmount) >= 0) {

            throw validationFailure(
                    traceId,
                    "PARTIAL_SETTLEMENT_NOT_LESS",
                    "PARTIAL payment settlement must be less than outstanding amount",
                    "ERR_PARTIAL_SETTLEMENT_MUST_BE_LESS_THAN_OUTSTANDING",
                    "amount"
            );
        }

        BigDecimal currentTaxableAmount =
                settlementAmount;

        BigDecimal currentGstAmount =
                BigDecimal.ZERO.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal maximumBankReceivable =
                safeOutstandingAmount
                        .multiply(netFactor)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        log.info(
                "[PAYMENT-TDS-CALCULATION] traceId={} | "
                        + "inputMeaning=ACTUAL_BANK_RECEIVED | "
                        + "paymentType={} | bankAmount={} | "
                        + "tdsPercentage={} | tdsAmount={} | "
                        + "settlementAmount={} | outstanding={}",
                traceId,
                paymentTypeCode,
                actualBankAmount,
                safeTdsPercentage,
                currentTdsAmount,
                settlementAmount,
                safeOutstandingAmount
        );

        return new CalculationValues(
                currentTaxableAmount,
                currentGstAmount,
                currentTdsAmount,
                actualBankAmount,
                settlementAmount,
                totalAllowedTds,
                remainingTdsLimit,
                maximumBankReceivable
        );
    }




    private void validatePaymentTypeRules(
            String traceId,
            String paymentTypeCode,
            BigDecimal settlementAmount,
            BigDecimal totalInvoiceAmount,
            BigDecimal outstandingAmount,
            Integer paymentTermsDays,
            BigDecimal installmentEligibleAmount
    ) {

        log.info(
                "[PAYMENT-TYPE-RULE] traceId={} | paymentType={} | "
                        + "settlement={} | outstanding={} | "
                        + "installmentEligible={} | paymentTermsDays={}",
                traceId,
                paymentTypeCode,
                settlementAmount,
                outstandingAmount,
                installmentEligibleAmount,
                paymentTermsDays
        );

        switch (paymentTypeCode) {

            case "FULL" -> {

                if (settlementAmount.compareTo(outstandingAmount) != 0) {
                    throw validationFailure(
                            traceId,
                            "FULL_SETTLEMENT_MISMATCH",
                            "FULL payment settlement must equal outstanding amount. "
                                    + "Settlement: ₹" + settlementAmount
                                    + ", outstanding: ₹" + outstandingAmount,
                            "ERR_FULL_AMOUNT_MISMATCH",
                            "amount"
                    );
                }
            }

            case "PARTIAL" -> {

                if (settlementAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw validationFailure(
                            traceId,
                            "PARTIAL_NOT_POSITIVE",
                            "PARTIAL settlement must be greater than zero",
                            "ERR_PARTIAL_SETTLEMENT_NOT_POSITIVE",
                            "amount"
                    );
                }

                BigDecimal halfAmount = totalInvoiceAmount.divide(
                        new BigDecimal("2"),
                        2,
                        RoundingMode.HALF_UP
                );

                BigDecimal expectedPartialAmount =
                        halfAmount.min(outstandingAmount);

                if (settlementAmount.compareTo(expectedPartialAmount) != 0) {
                    throw validationFailure(
                            traceId,
                            "PARTIAL_AMOUNT_MISMATCH",
                            "PARTIAL payment must be exactly ₹"
                                    + expectedPartialAmount
                                    + ". Total amount: ₹"
                                    + totalInvoiceAmount
                                    + ", outstanding: ₹"
                                    + outstandingAmount
                                    + ", settlement: ₹"
                                    + settlementAmount,
                            "ERR_PARTIAL_AMOUNT_MISMATCH",
                            "amount"
                    );
                }
            }


            case "INSTALLMENT" -> {

                if (settlementAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw validationFailure(
                            traceId,
                            "INSTALLMENT_NOT_POSITIVE",
                            "INSTALLMENT settlement must be greater than zero",
                            "ERR_INSTALLMENT_SETTLEMENT_NOT_POSITIVE",
                            "amount"
                    );
                }

                /*
                 * When milestone data is supplied, enforce it here.
                 */
                if (installmentEligibleAmount.compareTo(BigDecimal.ZERO) > 0
                        && settlementAmount.compareTo(
                        installmentEligibleAmount
                ) > 0) {

                    throw validationFailure(
                            traceId,
                            "INSTALLMENT_EXCEEDS_MILESTONE",
                            "INSTALLMENT settlement exceeds milestone eligible amount. "
                                    + "Settlement: ₹" + settlementAmount
                                    + ", milestone eligible amount: ₹"
                                    + installmentEligibleAmount,
                            "ERR_INSTALLMENT_EXCEEDS_MILESTONE_AMOUNT",
                            "amount"
                    );
                }

                if (installmentEligibleAmount.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn(
                            "[PAYMENT-MILESTONE-WARNING] traceId={} | "
                                    + "paymentType=INSTALLMENT | "
                                    + "milestone eligible amount was not supplied. "
                                    + "Only outstanding validation was applied.",
                            traceId
                    );
                }
            }

            case "PURCHASE_ORDER" -> {

                if (paymentTermsDays == null || paymentTermsDays < 0) {
                    throw validationFailure(
                            traceId,
                            "PO_TERMS_MISSING",
                            "Payment terms days is required for Purchase Order",
                            "ERR_PAYMENT_TERMS_DAYS_REQUIRED",
                            "paymentTermsDays"
                    );
                }

                /*
                 * Positive PO payment is the second stage.
                 * Project-completion validation remains in PaymentServiceImpl
                 * because it requires Operation Service.
                 */
            }

            default -> throw validationFailure(
                    traceId,
                    "UNSUPPORTED_PAYMENT_TYPE",
                    "Unsupported payment type: " + paymentTypeCode,
                    "ERR_UNSUPPORTED_PAYMENT_TYPE",
                    "paymentTypeId"
            );
        }
    }

    private void validateSettlementAgainstOutstanding(
            String traceId,
            BigDecimal settlementAmount,
            BigDecimal outstandingAmount
    ) {

        if (settlementAmount.compareTo(outstandingAmount) > 0) {
            throw validationFailure(
                    traceId,
                    "SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "Settlement amount exceeds outstanding amount. "
                            + "Settlement: ₹" + settlementAmount
                            + ", outstanding: ₹" + outstandingAmount,
                    "ERR_SETTLEMENT_EXCEEDS_OUTSTANDING",
                    "amount"
            );
        }
    }

    private void validateBasicAmounts(
            String traceId,
            BigDecimal bankAmount,
            BigDecimal totalTaxableAmount,
            BigDecimal totalInvoiceAmount,
            BigDecimal outstandingAmount
    ) {

        if (bankAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "BANK_AMOUNT_NOT_POSITIVE",
                    "Bank amount must be greater than zero",
                    "ERR_AMOUNT_NOT_POSITIVE",
                    "amount"
            );
        }

        if (totalTaxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "TAXABLE_AMOUNT_MISSING",
                    "Taxable amount excluding GST must be greater than zero",
                    "ERR_TDS_TAXABLE_AMOUNT_NOT_FOUND",
                    "taxableAmount"
            );
        }

        if (totalInvoiceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "INVOICE_AMOUNT_MISSING",
                    "Invoice or Estimate grand total must be greater than zero",
                    "ERR_TDS_TOTAL_AMOUNT_NOT_FOUND",
                    "totalInvoiceAmount"
            );
        }

        if (outstandingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "NO_OUTSTANDING",
                    "No outstanding amount is available for payment",
                    "ERR_NO_OUTSTANDING_FOR_PAYMENT",
                    "amount"
            );
        }
    }

    private void validateInvoiceComposition(
            String traceId,
            GstRegistrationType gstRegistrationType,
            BigDecimal taxableAmount,
            BigDecimal gstAmount,
            BigDecimal invoiceAmount
    ) {

        if (taxableAmount.compareTo(invoiceAmount) > 0) {
            throw validationFailure(
                    traceId,
                    "TAXABLE_EXCEEDS_INVOICE",
                    "Taxable amount cannot exceed Invoice total",
                    "ERR_INVALID_TDS_TAXABLE_AMOUNT",
                    "taxableAmount"
            );
        }

        if (isZeroRated(gstRegistrationType)
                && gstAmount.compareTo(BigDecimal.ZERO) != 0) {

            throw validationFailure(
                    traceId,
                    "ZERO_RATED_GST_FOUND",
                    gstRegistrationType
                            + " transaction must have zero GST. GST found: ₹"
                            + gstAmount,
                    "ERR_ZERO_RATED_GST_MUST_BE_ZERO",
                    "gstAmount"
            );
        }

        BigDecimal calculatedTotal =
                taxableAmount
                        .add(gstAmount)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal difference =
                invoiceAmount
                        .subtract(calculatedTotal)
                        .abs()
                        .setScale(2, RoundingMode.HALF_UP);

        /*
         * A ₹1 difference is tolerated because Estimate grand total
         * may be rounded at the header.
         */
        if (difference.compareTo(new BigDecimal("1.00")) > 0) {
            log.warn(
                    "[PAYMENT-INVOICE-COMPOSITION-WARNING] traceId={} | "
                            + "taxable={} | gst={} | calculatedTotal={} | "
                            + "invoiceTotal={} | difference={}",
                    traceId,
                    taxableAmount,
                    gstAmount,
                    calculatedTotal,
                    invoiceAmount,
                    difference
            );
        }
    }

    private void validateInternationalRules(
            String traceId,
            GstRegistrationType gstRegistrationType,
            boolean tdsActive,
            BigDecimal incomingTdsPercentage
    ) {

        if (gstRegistrationType != GstRegistrationType.INTERNATIONAL) {
            return;
        }

        if (tdsActive || incomingTdsPercentage != null) {
            throw validationFailure(
                    traceId,
                    "INTERNATIONAL_TDS_BLOCKED",
                    "TDS is not applicable for INTERNATIONAL transactions",
                    "ERR_TDS_NOT_ALLOWED_FOR_INTERNATIONAL",
                    "tdsActive"
            );
        }
    }

    private void validateTdsPercentage(
            String traceId,
            BigDecimal tdsPercentage
    ) {

        if (tdsPercentage.compareTo(TDS_TWO) != 0
                && tdsPercentage.compareTo(TDS_TEN) != 0) {

            throw validationFailure(
                    traceId,
                    "INVALID_TDS_PERCENTAGE",
                    "TDS percentage must be either 2 or 10",
                    "ERR_INVALID_TDS_PERCENTAGE",
                    "tds.tdsPercentage"
            );
        }
    }

    private BigDecimal calculateEffectiveGstPercentage(
            String traceId,
            GstRegistrationType gstRegistrationType,
            BigDecimal totalTaxableAmount,
            BigDecimal totalGstAmount
    ) {

        if (isZeroRated(gstRegistrationType)) {

            BigDecimal effectiveGstPercentage =
                    BigDecimal.ZERO.setScale(
                            8,
                            RoundingMode.HALF_UP
                    );

            log.info(
                    "[PAYMENT-EFFECTIVE-GST-CALCULATION] traceId={} | "
                            + "gstType={} | zeroRated=true | totalTaxable={} | "
                            + "totalGst={} | effectiveGstPercentage={}",
                    traceId,
                    gstRegistrationType,
                    totalTaxableAmount,
                    totalGstAmount,
                    effectiveGstPercentage
            );

            return effectiveGstPercentage;
        }

        if (totalTaxableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw validationFailure(
                    traceId,
                    "GST_RATE_TAXABLE_MISSING",
                    "Taxable amount is required to derive effective GST percentage",
                    "ERR_GST_TAXABLE_AMOUNT_REQUIRED",
                    "taxableAmount"
            );
        }

        BigDecimal effectiveGstPercentage =
                totalGstAmount
                        .multiply(HUNDRED)
                        .divide(
                                totalTaxableAmount,
                                8,
                                RoundingMode.HALF_UP
                        );

        log.info(
                "[PAYMENT-EFFECTIVE-GST-CALCULATION] traceId={} | "
                        + "gstType={} | zeroRated=false | formula=(totalGst * 100) / totalTaxable | "
                        + "totalTaxable={} | totalGst={} | effectiveGstPercentage={}",
                traceId,
                gstRegistrationType,
                totalTaxableAmount,
                totalGstAmount,
                effectiveGstPercentage
        );

        return effectiveGstPercentage;
    }

    private BigDecimal resolveTotalGstAmount(
            PaymentCalculationRequest request,
            BigDecimal totalTaxableAmount
    ) {

        if (request.getTotalGstAmount() != null) {

            BigDecimal resolvedGstAmount =
                    money(request.getTotalGstAmount());

            log.info(
                    "[PAYMENT-GST-AMOUNT-RESOLVED] traceId={} | "
                            + "estimateId={} | source=REQUEST_TOTAL_GST | "
                            + "incomingTotalGst={} | totalTaxable={} | "
                            + "totalInvoice={} | resolvedTotalGst={}",
                    request.getTraceId(),
                    request.getEstimateId(),
                    request.getTotalGstAmount(),
                    totalTaxableAmount,
                    money(request.getTotalInvoiceAmount()),
                    resolvedGstAmount
            );

            return resolvedGstAmount;
        }

        BigDecimal invoiceAmount =
                money(request.getTotalInvoiceAmount());

        BigDecimal resolvedGstAmount =
                invoiceAmount
                        .subtract(totalTaxableAmount)
                        .max(BigDecimal.ZERO)
                        .setScale(2, RoundingMode.HALF_UP);

        log.info(
                "[PAYMENT-GST-AMOUNT-RESOLVED] traceId={} | "
                        + "estimateId={} | source=INVOICE_MINUS_TAXABLE | "
                        + "incomingTotalGst=null | totalTaxable={} | "
                        + "totalInvoice={} | calculation={} - {} | "
                        + "resolvedTotalGst={}",
                request.getTraceId(),
                request.getEstimateId(),
                totalTaxableAmount,
                invoiceAmount,
                invoiceAmount,
                totalTaxableAmount,
                resolvedGstAmount
        );

        return resolvedGstAmount;
    }

    private void validateSupportedPaymentType(
            String traceId,
            String paymentTypeCode
    ) {

        if (!"FULL".equals(paymentTypeCode)
                && !"PARTIAL".equals(paymentTypeCode)
                && !"INSTALLMENT".equals(paymentTypeCode)
                && !"PURCHASE_ORDER".equals(paymentTypeCode)) {

            throw validationFailure(
                    traceId,
                    "PAYMENT_TYPE_INVALID",
                    "Unsupported payment type: " + paymentTypeCode,
                    "ERR_UNSUPPORTED_PAYMENT_TYPE",
                    "paymentTypeId"
            );
        }
    }

    private boolean isZeroRated(
            GstRegistrationType gstRegistrationType
    ) {
        return gstRegistrationType == GstRegistrationType.SEZ
                || gstRegistrationType
                == GstRegistrationType.INTERNATIONAL;
    }

    private String buildScenario(
            GstRegistrationType gstRegistrationType,
            String paymentTypeCode,
            boolean tdsActive,
            BigDecimal bankAmount
    ) {

        if ("PURCHASE_ORDER".equals(paymentTypeCode)
                && bankAmount.compareTo(BigDecimal.ZERO) == 0) {

            return gstRegistrationType.name()
                    + "_PURCHASE_ORDER_INITIAL_ZERO";
        }

        return gstRegistrationType.name()
                + "_"
                + paymentTypeCode
                + "_"
                + (tdsActive ? "WITH_TDS" : "WITHOUT_TDS");
    }

    private String normalizePaymentType(String paymentTypeCode) {
        if (paymentTypeCode == null
                || paymentTypeCode.trim().isEmpty()) {

            return "";
        }

        return paymentTypeCode
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String resolveTraceId(String traceId) {
        if (traceId != null && !traceId.trim().isEmpty()) {
            return traceId.trim();
        }

        return UUID.randomUUID().toString();
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? ZERO
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(
                8,
                RoundingMode.HALF_UP
        )
                : value.setScale(
                8,
                RoundingMode.HALF_UP
        );
    }

    private ValidationException validationFailure(
            String traceId,
            String condition,
            String message,
            String errorCode,
            String field
    ) {

        log.warn(
                "[PAYMENT-CALC-FAILED] traceId={} | condition={} | "
                        + "errorCode={} | field={} | message={}",
                traceId,
                condition,
                errorCode,
                field,
                message
        );

        return new ValidationException(
                message,
                errorCode,
                field
        );
    }

    private record CalculationValues(
            BigDecimal currentTaxableAmount,
            BigDecimal currentGstAmount,
            BigDecimal tdsAmount,
            BigDecimal actualBankAmount,
            BigDecimal settlementAmount,
            BigDecimal totalAllowedTds,
            BigDecimal remainingTdsLimit,
            BigDecimal maximumBankReceivable
    ) {
    }

    @Getter
    @Builder
    public static class PaymentCalculationRequest {

        private String traceId;

        private Long estimateId;
        private Long unbilledId;
        private Long invoiceId;

        private GstRegistrationType gstRegistrationType;
        private String paymentTypeCode;

        /**
         * Actual Bank/Cash/Payment Gateway amount.
         */
        private BigDecimal bankAmount;

        private Boolean tdsActive;
        private BigDecimal tdsPercentage;

        /**
         * Complete Estimate or Invoice values.
         */
        private BigDecimal totalTaxableAmount;
        private BigDecimal totalGstAmount;
        private BigDecimal totalInvoiceAmount;

        /**
         * Available amount before current registration.
         */
        private BigDecimal outstandingAmount;

        /**
         * PENDING + APPROVED TDS already registered.
         */
        private BigDecimal alreadyUsedTds;

        private Integer paymentTermsDays;

        /**
         * Optional until milestone integration is complete.
         */
        private BigDecimal installmentEligibleAmount;
    }




    @Getter
    @Builder
    public static class PaymentCalculationResult {

        private String traceId;
        private String scenario;

        private GstRegistrationType gstRegistrationType;
        private String paymentTypeCode;
        private boolean tdsActive;

        private BigDecimal bankAmount;
        private BigDecimal currentTaxableAmount;
        private BigDecimal currentGstAmount;

        private BigDecimal tdsPercentage;
        private BigDecimal tdsAmount;
        private BigDecimal settlementAmount;

        private BigDecimal effectiveGstPercentage;
        private BigDecimal totalAllowedTds;
        private BigDecimal alreadyUsedTds;
        private BigDecimal remainingTdsLimit;
        private BigDecimal maximumBankReceivable;

        private BigDecimal outstandingBefore;
        private BigDecimal outstandingAfter;

        private boolean initialPurchaseOrder;
        private boolean valid;
        private BigDecimal enteredTaxableAmount;
        private BigDecimal actualBankAmount;

    }
}