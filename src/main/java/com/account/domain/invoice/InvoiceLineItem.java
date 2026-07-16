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
                @Index(name = "idx_invoice_line_item_invoice_id", columnList = "invoice_id"),
                @Index(name = "idx_invoice_line_item_display_order", columnList = "display_order")
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

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(length = 255)
    private String description;

    @Column(name = "hsn_sac_code", length = 50)
    private String hsnSacCode;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(length = 20)
    private String unit;

    @Column(name = "unit_price_ex_gst", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPriceExGst = BigDecimal.ZERO;

    @Column(name = "gst_rate", precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    /** false = CGST + SGST, true = IGST. */
    @Column(name = "igst_flag", nullable = false)
    private boolean igstFlag = false;

    @Column(name = "line_total_ex_gst", precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotalExGst = BigDecimal.ZERO;

    @Column(name = "gst_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal gstAmount = BigDecimal.ZERO;

    @Column(name = "line_total_with_gst", precision = 15, scale = 2, nullable = false)
    private BigDecimal lineTotalWithGst = BigDecimal.ZERO;

    @Column(name = "cgst_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "category_code", length = 100)
    private String categoryCode;

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled = false;

    @Column(name = "fee_type", length = 50)
    private String feeType;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void calculateLineTotals() {
        quantity = quantity != null && quantity > 0 ? quantity : 1;
        unitPriceExGst = safeMoney(unitPriceExGst);
        gstRate = safeMoney(gstRate);

        lineTotalExGst = unitPriceExGst
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);

        gstAmount = gstRate.compareTo(BigDecimal.ZERO) > 0
                ? lineTotalExGst
                .multiply(gstRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : zeroMoney();

        lineTotalWithGst = lineTotalExGst
                .add(gstAmount)
                .setScale(2, RoundingMode.HALF_UP);

        if (gstAmount.compareTo(BigDecimal.ZERO) == 0) {
            cgstAmount = zeroMoney();
            sgstAmount = zeroMoney();
            igstAmount = zeroMoney();
            return;
        }

        if (igstFlag) {
            igstAmount = safeMoney(gstAmount);
            cgstAmount = zeroMoney();
            sgstAmount = zeroMoney();
            return;
        }

        cgstAmount = gstAmount.divide(
                BigDecimal.valueOf(2),
                2,
                RoundingMode.HALF_UP
        );

        sgstAmount = gstAmount
                .subtract(cgstAmount)
                .setScale(2, RoundingMode.HALF_UP);

        igstAmount = zeroMoney();
    }

    private static BigDecimal safeMoney(BigDecimal value) {
        return value == null
                ? zeroMoney()
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
