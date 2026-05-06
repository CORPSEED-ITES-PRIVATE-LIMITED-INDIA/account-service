package com.account.domain;

import com.account.domain.estimate.Estimate;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tds_registration",
        indexes = {
                @Index(name = "idx_tds_public_uuid_unique", columnList = "public_uuid", unique = true),
                @Index(name = "idx_tds_unbilled_unique", columnList = "unbilled_invoice_id", unique = true),
                @Index(name = "idx_tds_estimate_id", columnList = "estimate_id"),
                @Index(name = "idx_tds_status", columnList = "status")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"estimate", "unbilledInvoice"})
public class TdsRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unbilled_invoice_id", nullable = false, unique = true)
    private UnbilledInvoice unbilledInvoice;

    @Column(name = "tds_percentage", precision = 5, scale = 2, nullable = false)
    private BigDecimal tdsPercentage;

    @Column(name = "taxable_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal taxableAmount;

    @Column(name = "tds_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal tdsAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TdsStatus status = TdsStatus.PENDING;

    @Column(nullable = false)
    private boolean isDeleted = false;

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
}