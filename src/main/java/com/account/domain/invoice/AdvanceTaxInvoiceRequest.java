package com.account.domain.invoice;

import com.account.domain.User;
import com.account.domain.estimate.Estimate;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "advance_tax_invoice_request",
        indexes = {
                @Index(name = "idx_advance_tax_invoice_request_uuid", columnList = "public_uuid", unique = true),
                @Index(name = "idx_advance_tax_invoice_request_estimate", columnList = "estimate_id"),
                @Index(name = "idx_advance_tax_invoice_request_status", columnList = "status"),
                @Index(name = "idx_advance_tax_invoice_request_requested_by", columnList = "requested_by"),
                @Index(name = "idx_advance_tax_invoice_request_reviewed_by", columnList = "reviewed_by"),
                @Index(name = "idx_advance_tax_invoice_request_created_at", columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"estimate", "requestedBy", "reviewedBy", "invoice"})
public class AdvanceTaxInvoiceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_uuid", nullable = false, unique = true, length = 36)
    private String publicUuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private Estimate estimate;

    @Column(name = "requested_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AdvanceTaxInvoiceRequestStatus status = AdvanceTaxInvoiceRequestStatus.PENDING;

    @Column(name = "request_remarks", columnDefinition = "TEXT")
    private String requestRemarks;

    @Column(name = "review_remarks", columnDefinition = "TEXT")
    private String reviewRemarks;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToOne(mappedBy = "advanceTaxInvoiceRequest", fetch = FetchType.LAZY)
    private Invoice invoice;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (publicUuid == null || publicUuid.isBlank()) {
            publicUuid = UUID.randomUUID().toString();
        }
        if (status == null) {
            status = AdvanceTaxInvoiceRequestStatus.PENDING;
        }
    }
}
