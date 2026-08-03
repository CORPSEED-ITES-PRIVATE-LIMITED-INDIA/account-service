package com.account.dto.ledger;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountingVoucherEntryRequestDto {

    private static final int MONEY_SCALE = 3;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_UP;

    @NotNull(message = "Ledger ID is required")
    @Positive(message = "Ledger ID must be greater than zero")
    private Long ledgerId;

    /*
     * Debit amount must support three-decimal accounting precision.
     *
     * Examples:
     * 563.000
     * 0.228
     * 521.380
     */
    @Builder.Default
    @NotNull(message = "Debit amount is required")
    @PositiveOrZero(message = "Debit amount cannot be negative")
    @Digits(
            integer = 16,
            fraction = 3,
            message = "Debit amount can have a maximum of 3 decimal places"
    )
    private BigDecimal debitAmount = zeroMoney();

    /*
     * Credit amount must support three-decimal accounting precision.
     *
     * Examples:
     * 615.000
     * 93.848
     * 52.000
     */
    @Builder.Default
    @NotNull(message = "Credit amount is required")
    @PositiveOrZero(message = "Credit amount cannot be negative")
    @Digits(
            integer = 16,
            fraction = 3,
            message = "Credit amount can have a maximum of 3 decimal places"
    )
    private BigDecimal creditAmount = zeroMoney();

    @Size(
            max = 1000,
            message = "Narration cannot exceed 1000 characters"
    )
    private String narration;

    /**
     * Returns the normalized debit amount using three-decimal
     * mathematical HALF_UP rounding.
     */
    public BigDecimal normalizedDebitAmount() {

        return normalizeMoney(this.debitAmount);
    }

    /**
     * Returns the normalized credit amount using three-decimal
     * mathematical HALF_UP rounding.
     */
    public BigDecimal normalizedCreditAmount() {

        return normalizeMoney(this.creditAmount);
    }

    /**
     * Returns true when the entry contains a debit amount.
     */
    public boolean hasDebitAmount() {

        return normalizedDebitAmount()
                .compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Returns true when the entry contains a credit amount.
     */
    public boolean hasCreditAmount() {

        return normalizedCreditAmount()
                .compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Business validation:
     *
     * A voucher entry must have either debit or credit,
     * but it cannot have both.
     *
     * This validation should still remain inside
     * AccountingVoucherServiceImpl.
     */
    public boolean hasValidDebitCreditCombination() {

        boolean debitPresent = hasDebitAmount();
        boolean creditPresent = hasCreditAmount();

        return debitPresent != creditPresent;
    }

    private static BigDecimal normalizeMoney(
            BigDecimal value
    ) {

        return value == null
                ? zeroMoney()
                : value.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }

    private static BigDecimal zeroMoney() {

        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }
}