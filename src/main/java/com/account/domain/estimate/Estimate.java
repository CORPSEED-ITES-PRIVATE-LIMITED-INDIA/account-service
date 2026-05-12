package com.account.domain.estimate;

import com.account.domain.*;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "estimate",
        indexes = {
                @Index(name = "idx_estimate_number_unique", columnList = "estimate_number", unique = true),
                @Index(name = "idx_estimate_public_uuid_unique", columnList = "public_uuid", unique = true),
                @Index(name = "idx_estimate_status", columnList = "status"),
                @Index(name = "idx_estimate_company_id", columnList = "company_id"),
                @Index(name = "idx_estimate_unit_id", columnList = "unit_id"),
                @Index(name = "idx_estimate_parent_estimate_id", columnList = "parent_estimate_id")
        })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"lineItems", "parentEstimate"})
public class Estimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid;

    private Long leadId;
    private Long proposalId;

    // Human-readable unique number shown to customer (e.g. EST-2026-001234)
    @Column(name = "estimate_number", nullable = false, unique = true, length = 32)
    private String estimateNumber;

    @Column(name = "performance_invoice_number", nullable = false, unique = true, length = 32)
    private String performanceInvoiceNumber;

    private boolean performanceInvoiceFlag=false;

    @Column(name = "estimate_date", nullable = false)
    private LocalDate estimateDate = LocalDate.now();

    @Column(name = "valid_until")
    private LocalDate validUntil;

    // Reference to originating solution(s)
    @Column(name = "solution_id", length = 500)
    private Long solutionId;

    @Column(name = "solution_name", nullable = false, length = 255)
    private String solutionName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(length = 50)
    private String solutionType;

    @OneToMany(mappedBy = "estimate", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "display_order")
    private List<EstimateLineItem> lineItems = new ArrayList<>();

    // Calculated totals
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal subTotalExGst = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal totalGstAmount = BigDecimal.ZERO;

    // GST breakup - important for GSTR-1 and customer visibility
    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(length = 2)
    private String placeOfSupplyStateCode;  // e.g. "06" for Haryana

    @Column(precision = 15, scale = 2, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private EstimateStatus status = EstimateStatus.DRAFT;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @Column(columnDefinition = "TEXT")
    private String customerNotes;

    @Column(columnDefinition = "TEXT")
    private String internalRemarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_estimate_id")
    private Estimate parentEstimate;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(columnDefinition = "TEXT")
    private String revisionReason;

    private boolean isDeleted = false;

    private boolean isCancelled = false;

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

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private LocalDateTime rejectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    @Column
    private LocalDateTime sentToClientAt;

    @Column(length = 255)
    private String sentToEmail;         // the email we actually sent to

    @Column(length = 100)
    private String sentByUserName;      // optional - who triggered send

    // Optional - if you want to allow resending to different email
    @Column(length = 500)
    private String lastSentEmails;      // comma separated if multiple

    @PrePersist
    protected void onCreate() {
        if (estimateDate == null) {
            estimateDate = LocalDate.now();
        }
        if (validUntil == null) {
            validUntil = estimateDate.plusDays(30);
        }
        if (publicUuid == null) {
            publicUuid = UUID.randomUUID().toString();
        }
        isDeleted = false;
    }

    /**
     * Recalculates all totals from line items including GST breakup.
     * Call this before saving if line items change.
     */
    public void calculateTotals() {
        this.subTotalExGst = lineItems.stream()
                .map(EstimateLineItem::getLineTotalExGst)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.totalGstAmount = lineItems.stream()
                .map(EstimateLineItem::getGstAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.cgstAmount = BigDecimal.ZERO;
        this.sgstAmount = BigDecimal.ZERO;
        this.igstAmount = BigDecimal.ZERO;

        if (lineItems != null && !lineItems.isEmpty()) {
            for (EstimateLineItem item : lineItems) {
                if (item == null || item.getGstAmount() == null) {
                    continue;
                }

                BigDecimal itemGstAmount = item.getGstAmount();

                if (Boolean.TRUE.equals(item.getIgstFlag())) {
                    this.igstAmount = this.igstAmount.add(itemGstAmount);
                } else {
                    BigDecimal halfGstAmount = itemGstAmount.divide(
                            BigDecimal.valueOf(2),
                            2,
                            java.math.RoundingMode.HALF_UP
                    );

                    this.cgstAmount = this.cgstAmount.add(halfGstAmount);
                    this.sgstAmount = this.sgstAmount.add(halfGstAmount);
                }
            }
        }

        this.grandTotal = this.subTotalExGst.add(this.totalGstAmount);
    }
}