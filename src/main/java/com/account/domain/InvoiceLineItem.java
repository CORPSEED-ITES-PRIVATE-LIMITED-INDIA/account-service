package com.account.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_line_item",
        indexes = {
                @Index(name = "idx_invoice_line_item_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_invoice_line_item_display_order", columnList = "display_order")
        })
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

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPriceExGst;

    @Column(precision = 5, scale = 2)
    private BigDecimal gstRate;

    // Calculated
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotalExGst;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal gstAmount;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotalWithGst;

    // GST breakup per line (for GSTR-1)
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

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

        this.lineTotalWithGst = lineTotalExGst.add(gstAmount);

        // Split GST (same logic as invoice header)
        BigDecimal halfGst = gstAmount.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        this.cgstAmount = halfGst;
        this.sgstAmount = halfGst;
        this.igstAmount = BigDecimal.ZERO;

        // Override with IGST if needed (based on placeOfSupplyStateCode from invoice)
        // if (invoice != null && invoice.getPlaceOfSupplyStateCode() != null &&
        //     !invoice.getPlaceOfSupplyStateCode().equals("seller_state")) {
        //     this.igstAmount = gstAmount;
        //     this.cgstAmount = BigDecimal.ZERO;
        //     this.sgstAmount = BigDecimal.ZERO;
        // }
    }
}