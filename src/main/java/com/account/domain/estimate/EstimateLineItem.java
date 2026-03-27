package com.account.domain.estimate;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "estimate_line_item",
        indexes = {
                @Index(name = "idx_line_item_estimate_id", columnList = "estimate_id"),
                @Index(name = "idx_line_item_display_order", columnList = "display_order")
        })
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

    @Column(length = 50)
    private String hsnSacCode;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(length = 20)
    private String unit;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPriceExGst = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;


    @Column(precision = 5, scale = 2)
    private BigDecimal igstRate = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2)
    private BigDecimal cgstRate = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2)
    private BigDecimal sgstRate = BigDecimal.ZERO;

    private Boolean igstFlag = true;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotalExGst = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(length = 100)
    private String categoryCode;

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

        // ===============================
        // 1. CALCULATE BASE AMOUNT
        // ===============================
        this.lineTotalExGst = unitPriceExGst
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        // ===============================
        // 2. GST SPLIT LOGIC
        // ===============================
        if (gstRate != null && gstRate.compareTo(BigDecimal.ZERO) > 0) {

            boolean isIgst = igstFlag == null || igstFlag;

            if (isIgst) {
                // IGST case
                this.igstRate = gstRate;

                this.cgstRate = BigDecimal.ZERO;
                this.sgstRate = BigDecimal.ZERO;

            } else {
                // CGST + SGST case
                BigDecimal halfGst = gstRate.divide(
                        BigDecimal.valueOf(2),
                        2,
                        java.math.RoundingMode.HALF_UP
                );

                this.cgstRate = halfGst;
                this.sgstRate = halfGst;

                this.igstRate = BigDecimal.ZERO;
            }

            // ===============================
            // 3. GST AMOUNT CALCULATION
            // ===============================
            this.gstAmount = lineTotalExGst.multiply(
                    gstRate.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP)
            ).setScale(2, java.math.RoundingMode.HALF_UP);

        } else {
            // No GST
            this.gstAmount = BigDecimal.ZERO;

            this.igstRate = BigDecimal.ZERO;
            this.cgstRate = BigDecimal.ZERO;
            this.sgstRate = BigDecimal.ZERO;
        }
    }
}