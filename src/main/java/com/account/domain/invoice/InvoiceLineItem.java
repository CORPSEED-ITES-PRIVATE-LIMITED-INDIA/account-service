package com.account.domain.invoice;

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
        name = "invoice_line_item",
        indexes = {
                @Index(
                        name = "idx_invoice_line_item_invoice_id",
                        columnList = "invoice_id"
                ),
                @Index(
                        name = "idx_invoice_line_item_display_order",
                        columnList = "display_order"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "invoice")
public class InvoiceLineItem {

    /*
     * Monetary amounts are maintained at three decimal places.
     *
     * The final Invoice/document total is rounded separately to
     * a whole rupee in InvoiceServiceImpl.
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
            name = "invoice_id",
            nullable = false
    )
    private Invoice invoice;

    @Column(name = "source_estimate_line_item_id")
    private Long sourceEstimateLineItemId;

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

    /*
     * Taxable unit price before GST.
     */
    @Column(
            name = "unit_price_ex_gst",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal unitPriceExGst =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );

    /*
     * GST percentage.
     *
     * Examples:
     * 18.000
     * 12.000
     * 5.000
     */
    @Column(
            name = "gst_rate",
            precision = 7,
            scale = 3,
            nullable = false
    )
    private BigDecimal gstRate =
            BigDecimal.ZERO.setScale(
                    RATE_SCALE,
                    ROUNDING_MODE
            );

    /**
     * false = CGST + SGST
     * true  = IGST
     */
    @Column(
            name = "igst_flag",
            nullable = false
    )
    private boolean igstFlag = false;

    /*
     * quantity × unit price before GST.
     */
    @Column(
            name = "line_total_ex_gst",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal lineTotalExGst =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );

    /*
     * Complete GST amount for this line.
     */
    @Column(
            name = "gst_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal gstAmount =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );

    /*
     * Taxable value + GST.
     */
    @Column(
            name = "line_total_with_gst",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal lineTotalWithGst =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );

    @Column(
            name = "cgst_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal cgstAmount =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );

    @Column(
            name = "sgst_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal sgstAmount =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );

    @Column(
            name = "igst_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal igstAmount =
            BigDecimal.ZERO.setScale(
                    MONEY_SCALE,
                    ROUNDING_MODE
            );

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(
            name = "category_code",
            length = 100
    )
    private String categoryCode;

    @Column(
            name = "is_cancelled",
            nullable = false
    )
    private boolean isCancelled = false;

    @Column(
            name = "fee_type",
            length = 50
    )
    private String feeType;

    @CreatedDate
    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Calculates all line-level taxable and GST amounts.
     *
     * Rules:
     *
     * 1. Taxable amount uses three decimals.
     * 2. GST amount uses three decimals.
     * 3. CGST, SGST and IGST use three decimals.
     * 4. Mathematical HALF_UP rounding is used.
     * 5. The line total is not rounded to a whole rupee.
     * 6. Whole-rupee rounding happens only at document level.
     */
    @PrePersist
    @PreUpdate
    public void calculateLineTotals() {

        normalizeInputValues();

        /*
         * Taxable value:
         *
         * Unit price × Quantity
         */
        this.lineTotalExGst =
                this.unitPriceExGst
                        .multiply(
                                BigDecimal.valueOf(
                                        this.quantity
                                )
                        )
                        .setScale(
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );

        /*
         * GST:
         *
         * Taxable value × GST percentage ÷ 100
         */
        if (this.gstRate.compareTo(BigDecimal.ZERO) > 0) {

            this.gstAmount =
                    this.lineTotalExGst
                            .multiply(this.gstRate)
                            .divide(
                                    HUNDRED,
                                    MONEY_SCALE,
                                    ROUNDING_MODE
                            );

        } else {
            this.gstAmount = zeroMoney();
        }

        /*
         * Raw line total including GST.
         */
        this.lineTotalWithGst =
                this.lineTotalExGst
                        .add(this.gstAmount)
                        .setScale(
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );

        calculateGstBreakup();
    }

    /**
     * Normalizes quantity, unit price and GST rate before calculation.
     */
    private void normalizeInputValues() {

        if (this.quantity == null || this.quantity <= 0) {
            this.quantity = 1;
        }

        this.unitPriceExGst =
                safeMoney(this.unitPriceExGst);

        this.gstRate =
                safeRate(this.gstRate);
    }

    /**
     * Calculates CGST/SGST or IGST breakup.
     *
     * For intra-state:
     *
     * CGST = GST ÷ 2
     * SGST = GST − CGST
     *
     * SGST is calculated as the remaining amount so:
     *
     * CGST + SGST = Exact GST amount.
     */
    private void calculateGstBreakup() {

        if (this.gstAmount.compareTo(BigDecimal.ZERO) == 0) {

            this.cgstAmount = zeroMoney();
            this.sgstAmount = zeroMoney();
            this.igstAmount = zeroMoney();

            return;
        }

        if (this.igstFlag) {

            this.igstAmount =
                    safeMoney(this.gstAmount);

            this.cgstAmount = zeroMoney();
            this.sgstAmount = zeroMoney();

            return;
        }

        /*
         * CGST receives mathematically rounded half.
         */
        this.cgstAmount =
                this.gstAmount.divide(
                        TWO,
                        MONEY_SCALE,
                        ROUNDING_MODE
                );

        /*
         * SGST receives the remaining value.
         *
         * This avoids:
         *
         * CGST + SGST != Total GST
         */
        this.sgstAmount =
                this.gstAmount
                        .subtract(this.cgstAmount)
                        .setScale(
                                MONEY_SCALE,
                                ROUNDING_MODE
                        );

        this.igstAmount = zeroMoney();
    }

    /**
     * Returns an amount with three-decimal mathematical rounding.
     */
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

    /**
     * Returns a percentage with three decimal places.
     */
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