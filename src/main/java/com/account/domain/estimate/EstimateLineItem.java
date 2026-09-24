package com.account.domain.estimate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "estimate_line_item",
        indexes = {
                @Index(
                        name = "idx_line_item_estimate_id",
                        columnList = "estimate_id"
                ),
                @Index(
                        name = "idx_line_item_display_order",
                        columnList = "display_order"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "estimate")
public class EstimateLineItem {

    /*
     * Financial rounding standard:
     *
     * Taxable value = 3 decimals
     * GST amount    = 3 decimals
     * GST rate      = 3 decimals
     *
     * Final Estimate grand total is rounded to a whole rupee
     * inside the Estimate entity/service, not inside this line item.
     */
    private static final int MONEY_SCALE = 3;
    private static final int RATE_SCALE = 3;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_UP;

    private static final BigDecimal HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal TWO =
            new BigDecimal("2");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "estimate_id",
            nullable = false
    )
    private Estimate estimate;

    @Column(name = "source_item_id")
    private Long sourceItemId;

    @Column(
            name = "item_name",
            nullable = false,
            length = 255
    )
    private String itemName;

    @Column(length = 255)
    private String description;

    @Column(
            name = "hsn_sac_code",
            length = 50
    )
    private String hsnSacCode;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(length = 20)
    private String unit;

    // =====================================================
    // PRICE AND GST RATES
    // =====================================================

    @Column(
            name = "unit_price_ex_gst",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal unitPriceExGst = zeroMoney();

    @Column(
            name = "gst_rate",
            precision = 7,
            scale = 3,
            nullable = false
    )
    private BigDecimal gstRate = zeroRate();

    @Column(
            name = "igst_rate",
            precision = 7,
            scale = 3,
            nullable = false
    )
    private BigDecimal igstRate = zeroRate();

    @Column(
            name = "cgst_rate",
            precision = 7,
            scale = 3,
            nullable = false
    )
    private BigDecimal cgstRate = zeroRate();

    @Column(
            name = "sgst_rate",
            precision = 7,
            scale = 3,
            nullable = false
    )
    private BigDecimal sgstRate = zeroRate();

    /*
     * true  = IGST
     * false = CGST + SGST
     */
    @Column(
            name = "igst_flag",
            nullable = false
    )
    private Boolean igstFlag = Boolean.TRUE;

    // =====================================================
    // CALCULATED AMOUNTS
    // =====================================================

    @Column(
            name = "line_total_ex_gst",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal lineTotalExGst = zeroMoney();

    @Column(
            name = "gst_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal gstAmount = zeroMoney();

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(
            name = "category_code",
            length = 100
    )
    private String categoryCode;

    @Column(
            name = "fee_type",
            length = 50
    )
    private String feeType;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================

    @CreatedDate
    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // CALCULATION
    // =====================================================

    /**
     * Recalculates all taxable and GST values for this line.
     *
     * Rules:
     *
     * 1. Unit price uses three decimal places.
     * 2. Taxable line value uses three decimal places.
     * 3. GST amount uses three decimal places.
     * 4. GST rate breakup uses three decimal places.
     * 5. HALF_UP rounding is used.
     * 6. SEZ and International supplies always have zero GST.
     * 7. Document-level whole-rupee rounding is not done here.
     */
    @PrePersist
    @PreUpdate
    public void calculateLineTotals() {

        normalizeInputs();
        validateInputs();

        /*
         * Taxable line amount:
         *
         * Unit price × Quantity
         */
        this.lineTotalExGst =
                this.unitPriceExGst
                        .multiply(
                                BigDecimal.valueOf(this.quantity)
                        )
                        .setScale(
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );

        /*
         * SEZ and International supplies are zero-rated.
         */
        boolean zeroRatedSupply =
                this.estimate != null
                        && this.estimate.isZeroRatedSupply();

        if (zeroRatedSupply) {

            this.gstRate = zeroRate();
            this.gstAmount = zeroMoney();

            this.igstFlag = Boolean.TRUE;
            this.igstRate = zeroRate();
            this.cgstRate = zeroRate();
            this.sgstRate = zeroRate();

            return;
        }

        /*
         * No GST.
         */
        if (this.gstRate.compareTo(BigDecimal.ZERO) == 0) {

            this.gstAmount = zeroMoney();

            this.igstRate = zeroRate();
            this.cgstRate = zeroRate();
            this.sgstRate = zeroRate();

            return;
        }

        calculateGstRateBreakup();
        calculateGstAmount();
    }

    /**
     * Normalizes nullable or invalid quantity values and applies
     * three-decimal precision to amounts and rates.
     */
    private void normalizeInputs() {

        if (this.quantity == null || this.quantity <= 0) {
            this.quantity = 1;
        }

        this.unitPriceExGst =
                safeMoney(this.unitPriceExGst);

        this.gstRate =
                safeRate(this.gstRate);

        this.igstRate =
                safeRate(this.igstRate);

        this.cgstRate =
                safeRate(this.cgstRate);

        this.sgstRate =
                safeRate(this.sgstRate);

        if (this.igstFlag == null) {
            this.igstFlag = Boolean.TRUE;
        }
    }

    /**
     * Prevents negative unit prices and GST rates.
     */
    private void validateInputs() {

        if (this.unitPriceExGst.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Estimate line-item unit price cannot be negative"
            );
        }

        if (this.gstRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Estimate line-item GST rate cannot be negative"
            );
        }
    }

    /**
     * Splits the GST percentage into IGST or CGST and SGST.
     */
    private void calculateGstRateBreakup() {

        if (Boolean.TRUE.equals(this.igstFlag)) {

            this.igstFlag = Boolean.TRUE;

            this.igstRate =
                    safeRate(this.gstRate);

            this.cgstRate = zeroRate();
            this.sgstRate = zeroRate();

            return;
        }

        this.igstFlag = Boolean.FALSE;

        /*
         * CGST receives half of the GST rate.
         */
        this.cgstRate =
                this.gstRate.divide(
                        TWO,
                        RATE_SCALE,
                        ROUNDING_MODE
                );

        /*
         * SGST receives the remaining rate to guarantee:
         *
         * CGST rate + SGST rate = GST rate
         */
        this.sgstRate =
                this.gstRate
                        .subtract(this.cgstRate)
                        .setScale(
                                RATE_SCALE,
                                ROUNDING_MODE
                        );

        this.igstRate = zeroRate();
    }

    /**
     * GST amount:
     *
     * Taxable line value × GST rate ÷ 100
     */
    private void calculateGstAmount() {

        this.gstAmount =
                this.lineTotalExGst
                        .multiply(this.gstRate)
                        .divide(
                                HUNDRED,
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );
    }

    /**
     * Raw line total including GST.
     *
     * This remains at three decimal places.
     */
    public BigDecimal getLineTotalWithGst() {

        return safeMoney(this.lineTotalExGst)
                .add(safeMoney(this.gstAmount))
                .setScale(
                        MONEY_SCALE,
                        ROUNDING_MODE
                );
    }

    /**
     * Convenience helper for explicit IGST checks.
     */
    public boolean isIgstApplicable() {
        return Boolean.TRUE.equals(this.igstFlag);
    }

    private static BigDecimal safeMoney(
            BigDecimal value
    ) {

        return value == null
                ? zeroMoney()
                : value.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }

    private static BigDecimal safeRate(
            BigDecimal value
    ) {

        return value == null
                ? zeroRate()
                : value.setScale(
                RATE_SCALE,
                ROUNDING_MODE
        );
    }

    private static BigDecimal zeroMoney() {

        return BigDecimal.ZERO.setScale(
                MONEY_SCALE,
                ROUNDING_MODE
        );
    }

    private static BigDecimal zeroRate() {

        return BigDecimal.ZERO.setScale(
                RATE_SCALE,
                ROUNDING_MODE
        );
    }
}