package com.account.domain.estimate;

import com.account.domain.*;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "estimate",
        indexes = {
                @Index(name = "idx_estimate_number_unique",
                        columnList = "estimate_number",
                        unique = true),

                @Index(name = "idx_estimate_status", columnList = "status"),
                @Index(name = "idx_estimate_company_id", columnList = "company_id"),
                @Index(name = "idx_estimate_unit_id", columnList = "unit_id"),
                @Index(name = "idx_estimate_parent_estimate_id", columnList = "parent_estimate_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Estimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique identifier shown to customer (e.g. EST-2026-001234)
    @Column(name = "estimate_number", nullable = false, unique = true, length = 32)
    private String estimateNumber;

    @Column(name = "estimate_date", nullable = false)
    private LocalDate estimateDate = LocalDate.now();

    // Validity period
    @Column(name = "valid_until")
    private LocalDate validUntil;

    // Snapshot of main solution/product name (very useful for display & reporting)
    @Column(name = "solution_name", nullable = false, length = 255)
    private String solutionName; // e.g. "FSSAI State License - Uttar Pradesh" or "EPR - Plastic Packaging Compliance"

    // Client references
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit; // branch/outlet/plant - address comes from here

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // Reference to originating solution(s) - optional, for traceability
    @Column(name = "source_solution_ids", length = 500)
    private String sourceSolutionIds; // e.g. "456" or "567,789"

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SolutionType solutionType; // SERVICE / PRODUCT / PLANT_SETUP

    // Line items - the core breakdown
    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "display_order")
    private List<EstimateLineItem> lineItems = new ArrayList<>();

    // Final calculated values (stored for performance & audit)
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal subTotalExGst = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalGstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    // Status & lifecycle
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private EstimateStatus status = EstimateStatus.DRAFT;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @Column(columnDefinition = "TEXT")
    private String customerNotes;

    @Column(columnDefinition = "TEXT")
    private String internalRemarks;

    // Revision history support - immutable chain
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_estimate_id")
    private Estimate parentEstimate; // null for original

    @Column(nullable = false)
    private Integer version = 1;

    @Column(columnDefinition = "TEXT")
    private String revisionReason; // e.g. "Customer negotiated from 50k to 40k"

    private boolean isDeleted = false;

    // Auditing
    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (estimateDate == null) {
            estimateDate = LocalDate.now();
        }
        if (validUntil == null) {
            validUntil = estimateDate.plusDays(30); // Default 30 days validity
        }
        isDeleted = false;
    }

    /**
     * Recalculates totals from line items.
     * Call this before saving if any line item changes.
     */
    public void calculateTotals() {
        this.subTotalExGst = lineItems.stream()
                .map(EstimateLineItem::getLineTotalExGst)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalGstAmount = lineItems.stream()
                .map(EstimateLineItem::getGstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.grandTotal = subTotalExGst.add(totalGstAmount);
    }


}