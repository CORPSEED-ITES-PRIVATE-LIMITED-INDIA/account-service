package com.account.domain.invoice;

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "source_estimate_line_item_id")
    private Long sourceEstimateLineItemId;

    @Column(nullable = false, length = 255)
    private String itemName;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String hsnSacCode;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(length = 20)
    private String unit;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal unitPriceExGst;

    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    /*
     * false = CGST + SGST
     * true  = IGST
     */
    @Column(name = "igst_flag", nullable = false)
    private boolean igstFlag = false;

    // ==================== CALCULATED AMOUNTS ====================

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal lineTotalExGst = BigDecimal.ZERO;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal lineTotalWithGst = BigDecimal.ZERO;

    // GST breakup per line for GSTR-1

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(length = 100)
    private String categoryCode;

    private boolean isCancelled = false;

    @Column(length = 50)
    private String feeType;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;



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

        this.quantity = safeQuantity;

        this.unitPriceExGst = safeUnitPrice.setScale(
                2,
                RoundingMode.HALF_UP
        );

        this.gstRate = safeGstRate.setScale(
                2,
                RoundingMode.HALF_UP
        );

        // Quantity × unit price
        this.lineTotalExGst = this.unitPriceExGst
                .multiply(BigDecimal.valueOf(this.quantity))
                .setScale(2, RoundingMode.HALF_UP);

        // GST calculation
        if (this.gstRate.compareTo(BigDecimal.ZERO) > 0) {

            this.gstAmount = this.lineTotalExGst
                    .multiply(this.gstRate)
                    .divide(
                            BigDecimal.valueOf(100),
                            2,
                            RoundingMode.HALF_UP
                    );

        } else {

            /*
             * SEZ, INTERNATIONAL or other 0% GST item.
             */
            this.gstAmount =
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        this.lineTotalWithGst = this.lineTotalExGst
                .add(this.gstAmount)
                .setScale(2, RoundingMode.HALF_UP);

        // ==================== GST BREAKUP ====================

        if (this.gstAmount.compareTo(BigDecimal.ZERO) == 0) {

            this.cgstAmount =
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            this.sgstAmount =
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            this.igstAmount =
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

        } else if (this.igstFlag) {

            /*
             * Interstate transaction:
             * Entire GST amount is IGST.
             */
            this.igstAmount =
                    this.gstAmount.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            this.cgstAmount =
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            this.sgstAmount =
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

        } else {

            /*
             * Intrastate transaction:
             * GST is split between CGST and SGST.
             */
            BigDecimal calculatedCgst =
                    this.gstAmount.divide(
                            BigDecimal.valueOf(2),
                            2,
                            RoundingMode.HALF_UP
                    );

            /*
             * Calculate SGST as the remaining amount so that:
             *
             * CGST + SGST = exact GST amount
             *
             * This prevents a ₹0.01 rounding mismatch.
             */
            BigDecimal calculatedSgst =
                    this.gstAmount
                            .subtract(calculatedCgst)
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            this.cgstAmount = calculatedCgst;
            this.sgstAmount = calculatedSgst;

            this.igstAmount =
                    BigDecimal.ZERO.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );
        }
    }
}