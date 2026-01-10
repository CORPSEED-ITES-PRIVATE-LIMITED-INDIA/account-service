package com.account.domain.estimate;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "estimate_line_item",
        indexes = {
                @Index(name = "idx_line_item_estimate_id", columnList = "estimate_id"),
                @Index(name = "idx_line_item_display_order", columnList = "display_order")
        })
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

    // Reference to the original fee source (very useful for traceability & audit)
    @Column(name = "source_item_id")
    private Long sourceItemId;  // FeeComponent.id (Service) or ProductFeeRule.id (Product)

    // Snapshot of name at creation time (for display & immutability)
    @Column(nullable = false, length = 255)
    private String itemName;  // e.g. "PROFESSIONAL_CONSULTANCY" or "Recycling Target Fee"

    @Column(length = 255)
    private String description;  // Optional detailed description

    @Column(length = 50)
    private String hsnSacCode;  // HSN/SAC code for GST/invoicing

    // Quantity (critical for PRODUCT type, usually 1 for SERVICE)
    @Column(nullable = false)
    private Integer quantity = 1;

    // Unit (NOS, KG, METRIC_TON, FIXED, PACKAGE, etc.)
    @Column(length = 20)
    private String unit ;

    // Base price before GST
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPriceExGst = BigDecimal.ZERO;

    // GST rate for this specific line (0 for govt fees, 18 for professional/recycling, etc.)
    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    // Calculated fields (stored for performance, audit, and reporting)
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotalExGst = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal gstAmount = BigDecimal.ZERO;

    // Display order in PDF/quotation
    @Column(name = "display_order")
    private Integer displayOrder;

    // Optional - helpful for PRODUCT type reporting & filtering
    @Column(length = 100)
    private String categoryCode;  // e.g. "Plastic Bottles", "TVs & Monitors"

    // Optional - denormalized fee category for quick analytics
    @Column(length = 50)
    private String feeType;  // e.g. "GOVERNMENT", "PROFESSIONAL", "RECYCLING"

    // Auto-calculate totals before saving/updating
    @PrePersist
    @PreUpdate
    public void calculateLineTotals() {  // ← CHANGED to public
        this.lineTotalExGst = unitPriceExGst
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        if (gstRate != null && gstRate.compareTo(BigDecimal.ZERO) > 0) {
            this.gstAmount = lineTotalExGst.multiply(
                    gstRate.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP)
            ).setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            this.gstAmount = BigDecimal.ZERO;
        }
    }
}