package com.account.domain.estimate;

import com.account.domain.Contact;
import com.account.domain.User;
import com.account.domain.company.Company;
import com.account.domain.company.CompanyUnit;
import com.account.domain.company.GstRegistrationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "estimate",
        indexes = {
                @Index(
                        name = "idx_estimate_number_unique",
                        columnList = "estimate_number",
                        unique = true
                ),
                @Index(
                        name = "idx_estimate_public_uuid_unique",
                        columnList = "public_uuid",
                        unique = true
                ),
                @Index(
                        name = "idx_estimate_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_estimate_company_id",
                        columnList = "company_id"
                ),
                @Index(
                        name = "idx_estimate_unit_id",
                        columnList = "unit_id"
                ),
                @Index(
                        name = "idx_estimate_parent_estimate_id",
                        columnList = "parent_estimate_id"
                ),
                @Index(
                        name = "idx_estimate_gst_registration_type",
                        columnList = "gst_registration_type"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {
        "lineItems",
        "parentEstimate",
        "company",
        "unit",
        "contact",
        "createdBy",
        "updatedBy",
        "rejectedBy"
})
public class Estimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_uuid",
            nullable = false,
            unique = true,
            length = 36
    )
    private String publicUuid;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "proposal_id")
    private Long proposalId;

    @Column(
            name = "estimate_number",
            nullable = false,
            unique = true,
            length = 32
    )
    private String estimateNumber;

    @Column(
            name = "performance_invoice_number",
            nullable = false,
            unique = true,
            length = 32
    )
    private String performanceInvoiceNumber;

    @Column(
            name = "performance_invoice_flag",
            nullable = false
    )
    private boolean performanceInvoiceFlag = false;

    @Column(name = "estimate_date", nullable = false)
    private LocalDate estimateDate = LocalDate.now();

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "solution_id")
    private Long solutionId;

    @Column(
            name = "solution_name",
            nullable = false,
            length = 255
    )
    private String solutionName;

    @Column(name = "solution_type", length = 50)
    private String solutionType;

    // =====================================================
    // COMPANY, UNIT AND CONTACT
    // =====================================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    /*
     * GST registration type snapshot.
     *
     * This value is copied from CompanyUnit while creating
     * the estimate. Later CompanyUnit changes will not affect
     * the existing estimate.
     */
    @Enumerated(EnumType.STRING)
    @Column(
            name = "gst_registration_type",
            nullable = false,
            length = 30
    )
    private GstRegistrationType gstRegistrationType =
            GstRegistrationType.REGISTERED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // =====================================================
    // LINE ITEMS
    // =====================================================

    @OneToMany(
            mappedBy = "estimate",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("displayOrder ASC")
    private List<EstimateLineItem> lineItems = new ArrayList<>();

    // =====================================================
    // FINANCIAL TOTALS
    // =====================================================

    @Column(
            name = "sub_total_ex_gst",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal subTotalExGst = BigDecimal.ZERO;

    @Column(
            name = "total_gst_amount",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal totalGstAmount = BigDecimal.ZERO;

    @Column(
            name = "cgst_amount",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(
            name = "sgst_amount",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(
            name = "igst_amount",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(
            name = "place_of_supply_state_code",
            length = 2
    )
    private String placeOfSupplyStateCode;

    @Column(
            name = "grand_total",
            precision = 15,
            scale = 2,
            nullable = false
    )
    private BigDecimal grandTotal = BigDecimal.ZERO;

    // =====================================================
    // STATUS AND GENERAL DETAILS
    // =====================================================

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private EstimateStatus status = EstimateStatus.DRAFT;

    @Column(length = 3, nullable = false)
    private String currency = "INR";

    @Column(columnDefinition = "TEXT")
    private String customerNotes;

    @Column(columnDefinition = "TEXT")
    private String internalRemarks;

    // =====================================================
    // VERSIONING
    // =====================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_estimate_id")
    private Estimate parentEstimate;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(columnDefinition = "TEXT")
    private String revisionReason;

    // =====================================================
    // DELETE AND CANCEL FLAGS
    // =====================================================

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @Column(name = "is_cancelled", nullable = false)
    private boolean isCancelled = false;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // =====================================================
    // REJECTION DETAILS
    // =====================================================

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    // =====================================================
    // EMAIL DETAILS
    // =====================================================

    @Column(name = "sent_to_client_at")
    private LocalDateTime sentToClientAt;

    @Column(name = "sent_to_email", length = 255)
    private String sentToEmail;

    @Column(name = "sent_by_user_name", length = 100)
    private String sentByUserName;

    @Column(name = "last_sent_emails", length = 500)
    private String lastSentEmails;

    // =====================================================
    // ENTITY CALLBACK
    // =====================================================

    @PrePersist
    protected void onCreate() {

        if (estimateDate == null) {
            estimateDate = LocalDate.now();
        }

        if (validUntil == null) {
            validUntil = estimateDate.plusDays(30);
        }

        if (publicUuid == null || publicUuid.trim().isEmpty()) {
            publicUuid = UUID.randomUUID().toString();
        }

        if (gstRegistrationType == null) {
            gstRegistrationType =
                    GstRegistrationType.REGISTERED;
        }

        if (status == null) {
            status = EstimateStatus.DRAFT;
        }

        if (currency == null || currency.trim().isEmpty()) {
            currency = "INR";
        }

        if (version == null || version <= 0) {
            version = 1;
        }

        isDeleted = false;
    }

    // =====================================================
    // GST HELPERS
    // =====================================================

    public GstRegistrationType getEffectiveGstRegistrationType() {
        return gstRegistrationType != null
                ? gstRegistrationType
                : GstRegistrationType.REGISTERED;
    }

    public boolean isGstApplicable() {
        return getEffectiveGstRegistrationType()
                .isGstApplicable();
    }

    public boolean isZeroRatedSupply() {
        return getEffectiveGstRegistrationType()
                .isZeroRated();
    }

    public boolean isSezSupply() {
        return getEffectiveGstRegistrationType()
                == GstRegistrationType.SEZ;
    }

    public boolean isInternationalSupply() {
        return getEffectiveGstRegistrationType()
                == GstRegistrationType.INTERNATIONAL;
    }

    // =====================================================
    // LINE-ITEM MANAGEMENT
    // =====================================================

    public void addLineItem(EstimateLineItem lineItem) {
        if (lineItem == null) {
            return;
        }

        lineItem.setEstimate(this);
        this.lineItems.add(lineItem);
    }

    public void removeLineItem(EstimateLineItem lineItem) {
        if (lineItem == null) {
            return;
        }

        this.lineItems.remove(lineItem);
        lineItem.setEstimate(null);
    }

    // =====================================================
    // TOTAL CALCULATION
    // =====================================================

    /**
     * Recalculates all totals from estimate line items.
     *
     * REGISTERED / UNREGISTERED:
     *     GST is calculated from each line.
     *
     * SEZ / INTERNATIONAL:
     *     GST is forcefully set to zero.
     */
    public void calculateTotals() {

        if (lineItems == null || lineItems.isEmpty()) {
            resetAllTotals();
            return;
        }

        boolean zeroRatedSupply =
                isZeroRatedSupply();

        /*
         * Recalculate every line before calculating header totals.
         *
         * This also defensively forces GST to zero for SEZ and
         * INTERNATIONAL estimates.
         */
        for (EstimateLineItem lineItem : lineItems) {

            if (lineItem == null) {
                continue;
            }

            lineItem.setEstimate(this);

            if (zeroRatedSupply) {
                lineItem.setGstRate(
                        BigDecimal.ZERO.setScale(
                                2,
                                RoundingMode.HALF_UP
                        )
                );

                /*
                 * Zero-rated supply remains interstate classified,
                 * but the tax rate and tax amount remain zero.
                 */
                lineItem.setIgstFlag(true);
            }

            lineItem.calculateLineTotals();
        }

        this.subTotalExGst = lineItems.stream()
                .filter(Objects::nonNull)
                .map(EstimateLineItem::getLineTotalExGst)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Defensive zero-rated enforcement.
         */
        if (zeroRatedSupply) {

            this.totalGstAmount =
                    zeroMoney();

            this.cgstAmount =
                    zeroMoney();

            this.sgstAmount =
                    zeroMoney();

            this.igstAmount =
                    zeroMoney();

            this.grandTotal =
                    this.subTotalExGst.setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            return;
        }

        this.totalGstAmount = lineItems.stream()
                .filter(Objects::nonNull)
                .map(EstimateLineItem::getGstAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        this.cgstAmount =
                zeroMoney();

        this.sgstAmount =
                zeroMoney();

        this.igstAmount =
                zeroMoney();

        for (EstimateLineItem item : lineItems) {

            if (item == null
                    || item.getGstAmount() == null
                    || item.getGstAmount()
                    .compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            BigDecimal itemGstAmount =
                    item.getGstAmount().setScale(
                            2,
                            RoundingMode.HALF_UP
                    );

            if (Boolean.TRUE.equals(item.getIgstFlag())) {

                this.igstAmount =
                        this.igstAmount
                                .add(itemGstAmount)
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                );

            } else {

                BigDecimal itemCgstAmount =
                        itemGstAmount.divide(
                                BigDecimal.valueOf(2),
                                2,
                                RoundingMode.HALF_UP
                        );

                /*
                 * Remaining amount goes to SGST so:
                 *
                 * CGST + SGST = exact GST amount.
                 */
                BigDecimal itemSgstAmount =
                        itemGstAmount
                                .subtract(itemCgstAmount)
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                );

                this.cgstAmount =
                        this.cgstAmount
                                .add(itemCgstAmount)
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                );

                this.sgstAmount =
                        this.sgstAmount
                                .add(itemSgstAmount)
                                .setScale(
                                        2,
                                        RoundingMode.HALF_UP
                                );
            }
        }

        this.grandTotal =
                this.subTotalExGst
                        .add(this.totalGstAmount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );
    }

    private void resetAllTotals() {
        this.subTotalExGst = zeroMoney();
        this.totalGstAmount = zeroMoney();
        this.cgstAmount = zeroMoney();
        this.sgstAmount = zeroMoney();
        this.igstAmount = zeroMoney();
        this.grandTotal = zeroMoney();
    }

    private BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}