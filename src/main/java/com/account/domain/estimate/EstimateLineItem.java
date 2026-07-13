package com.account.domain.estimate;

import jakarta.persistence.*;
import lombok.*;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;

    @Column(name = "source_item_id")
    private Long sourceItemId;

    @Column(nullable = false, length = 255)
    private String itemName;

    @Column(length = 255)
    private String description;

    @Column(name = "hsn_sac_code", length = 50)
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
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal unitPriceExGst =
            BigDecimal.ZERO;

    @Column(
            name = "gst_rate",
            precision = 5,
            scale = 2,
            nullable = false
    )
    private BigDecimal gstRate =
            BigDecimal.ZERO;

    @Column(
            name = "igst_rate",
            precision = 5,
            scale = 2,
            nullable = false
    )
    private BigDecimal igstRate =
            BigDecimal.ZERO;

    @Column(
            name = "cgst_rate",
            precision = 5,
            scale = 2,
            nullable = false
    )
    private BigDecimal cgstRate =
            BigDecimal.ZERO;

    @Column(
            name = "sgst_rate",
            precision = 5,
            scale = 2,
            nullable = false
    )
    private BigDecimal sgstRate =
            BigDecimal.ZERO;

    /*
     * true  = IGST
     * false = CGST + SGST
     */
    @Column(name = "igst_flag", nullable = false)
    private Boolean igstFlag = true;

    // =====================================================
    // CALCULATED AMOUNTS
    // =====================================================

    @Column(
            name = "line_total_ex_gst",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal lineTotalExGst =
            BigDecimal.ZERO;

    @Column(
            name = "gst_amount",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal gstAmount =
            BigDecimal.ZERO;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(length = 100)
    private String categoryCode;

    @Column(length = 50)
    private String feeType;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // CALCULATION
    // =====================================================

    @PrePersist
    @PreUpdate
    public void calculateLineTotals() {

        Integer safeQuantity =
                quantity != null && quantity > 0
                        ? quantity
                        : 1;

        BigDecimal safeUnitPrice =
                unitPriceExGst != null
                        ? unitPriceExGst
                        : BigDecimal.ZERO;

        BigDecimal safeGstRate =
                gstRate != null
                        ? gstRate
                        : BigDecimal.ZERO;

        if (safeUnitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Estimate line-item unit price cannot be negative"
            );
        }

        if (safeGstRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Estimate line-item GST rate cannot be negative"
            );
        }

        this.quantity =
                safeQuantity;

        this.unitPriceExGst =
                safeUnitPrice.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        this.gstRate =
                safeGstRate.setScale(
                        2,
                        RoundingMode.HALF_UP
                );

        // =====================================================
        // BASE AMOUNT
        // =====================================================

        this.lineTotalExGst =
                this.unitPriceExGst
                        .multiply(
                                BigDecimal.valueOf(
                                        this.quantity
                                )
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        /*
         * Defensive zero-rated enforcement from parent estimate.
         */
        boolean zeroRatedSupply =
                estimate != null
                        && estimate.isZeroRatedSupply();

        if (zeroRatedSupply) {
            this.gstRate =
                    zeroRate();
        }

        // =====================================================
        // NO GST / ZERO-RATED SUPPLY
        // =====================================================

        if (this.gstRate.compareTo(BigDecimal.ZERO) <= 0) {

            this.gstAmount =
                    zeroMoney();

            this.igstRate =
                    zeroRate();

            this.cgstRate =
                    zeroRate();

            this.sgstRate =
                    zeroRate();

            return;
        }

        // =====================================================
        // GST RATE SPLIT
        // =====================================================

        boolean isIgst =
                this.igstFlag == null
                        || Boolean.TRUE.equals(
                        this.igstFlag
                );

        if (isIgst) {

            this.igstFlag = true;

            this.igstRate =
                    this.gstRate;

            this.cgstRate =
                    zeroRate();

            this.sgstRate =
                    zeroRate();

        } else {

            this.igstFlag = false;

            BigDecimal calculatedCgstRate =
                    this.gstRate.divide(
                            BigDecimal.valueOf(2),
                            2,
                            RoundingMode.HALF_UP
                    );

            /*
             * Remaining rate goes to SGST so that:
             *
             * CGST rate + SGST rate = GST rate.
             */
            BigDecimal calculatedSgstRate =
                    this.gstRate
                            .subtract(calculatedCgstRate)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            this.cgstRate =
                    calculatedCgstRate;

            this.sgstRate =
                    calculatedSgstRate;

            this.igstRate =
                    zeroRate();
        }

        // =====================================================
        // GST AMOUNT
        // =====================================================

        this.gstAmount =
                this.lineTotalExGst
                        .multiply(this.gstRate)
                        .divide(
                                BigDecimal.valueOf(100),
                                2,
                                RoundingMode.HALF_UP
                        );
    }

    public BigDecimal getLineTotalWithGst() {
        return safeMoney(lineTotalExGst)
                .add(safeMoney(gstAmount))
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value != null
                ? value.setScale(
                2,
                RoundingMode.HALF_UP
        )
                : zeroMoney();
    }

    private BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal zeroRate() {
        return BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}