package com.account.domain;

import com.account.domain.company.Company;
import com.account.domain.estimate.Estimate;
import com.account.domain.invoice.Invoice;
import com.account.domain.status.TdsStatus;
import com.account.domain.unbilled.UnbilledInvoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tds_registration",
        indexes = {
                @Index(
                        name = "idx_tds_registration_public_uuid",
                        columnList = "public_uuid",
                        unique = true
                ),
                @Index(
                        name = "idx_tds_registration_estimate_id",
                        columnList = "estimate_id"
                ),
                @Index(
                        name = "idx_tds_registration_company_id",
                        columnList = "company_id"
                ),
                @Index(
                        name = "idx_tds_registration_unbilled_id",
                        columnList = "unbilled_invoice_id"
                ),
                @Index(
                        name = "idx_tds_registration_invoice_id",
                        columnList = "invoice_id"
                ),
                @Index(
                        name = "uk_tds_registration_payment_receipt_id",
                        columnList = "payment_receipt_id",
                        unique = true
                ),
                @Index(
                        name = "idx_tds_registration_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_tds_registration_deleted",
                        columnList = "is_deleted"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {
        "estimate",
        "company",
        "unbilledInvoice",
        "invoice",
        "paymentReceipt",
        "createdBy",
        "updatedBy"
})
public class TdsRegistration {

    /*
     * Taxable amount and percentage are maintained at 3 decimals.
     *
     * Final TDS is first mathematically rounded to a whole rupee
     * and then stored with 3 decimal places.
     *
     * Example:
     *
     * Raw TDS   = 52.138
     * Final TDS = 52
     * Stored    = 52.000
     */
    private static final int MONEY_SCALE = 3;
    private static final int RATE_SCALE = 3;
    private static final int WHOLE_SCALE = 0;

    private static final RoundingMode ROUNDING_MODE =
            RoundingMode.HALF_UP;

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

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "estimate_id",
            nullable = false
    )
    private Estimate estimate;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "company_id",
            nullable = false
    )
    private Company company;

    /**
     * Normal payment source.
     *
     * Normal payment:
     * unbilledInvoice != null
     * invoice == null
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unbilled_invoice_id")
    private UnbilledInvoice unbilledInvoice;

    /**
     * Advance Tax Invoice payment source.
     *
     * ATI payment:
     * invoice != null
     * unbilledInvoice == null
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "payment_receipt_id",
            nullable = false,
            unique = true
    )
    private PaymentReceipt paymentReceipt;

    @Column(
            name = "tds_percentage",
            precision = 7,
            scale = 3,
            nullable = false
    )
    private BigDecimal tdsPercentage = zeroRate();

    @Column(
            name = "taxable_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal taxableAmount = zeroMoney();

    /*
     * TDS must be mathematically rounded to a whole rupee.
     *
     * It is stored with scale 3 for consistent accounting values:
     *
     * 52.000
     * 847.000
     */
    @Column(
            name = "tds_amount",
            precision = 19,
            scale = 3,
            nullable = false
    )
    private BigDecimal tdsAmount = zeroMoney();

    /**
     * Assigned during Accounts approval.
     */
    @Column(name = "tds_date")
    private LocalDate tdsDate;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private TdsStatus status = TdsStatus.PENDING;

    @Column(
            name = "is_deleted",
            nullable = false
    )
    private boolean isDeleted = false;

    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            updatable = false
    )
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(
            name = "created_at",
            updatable = false
    )
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        if (publicUuid == null || publicUuid.trim().isEmpty()) {
            publicUuid = UUID.randomUUID().toString();
        }

        if (status == null) {
            status = TdsStatus.PENDING;
        }

        normalizeFields();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeFields();
    }

    private void normalizeFields() {

        this.tdsPercentage =
                safeRate(this.tdsPercentage);

        this.taxableAmount =
                safeMoney(this.taxableAmount);

        /*
         * Mathematical whole-rupee rounding.
         *
         * Example:
         *
         * 52.138  -> 52.000
         * 52.500  -> 53.000
         * 355.575 -> 356.000
         */
        this.tdsAmount =
                safeWholeTds(this.tdsAmount);
    }

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

    private static BigDecimal safeWholeTds(
            BigDecimal value
    ) {

        if (value == null) {
            return zeroMoney();
        }

        return value
                .setScale(
                        WHOLE_SCALE,
                        ROUNDING_MODE
                )
                .setScale(
                        MONEY_SCALE,
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