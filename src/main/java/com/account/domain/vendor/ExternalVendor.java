package com.account.domain.vendor;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.ledger.LedgerMaster;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "external_vendors",
        indexes = {
                @Index(
                        name = "idx_external_vendor_operation_vendor",
                        columnList = "operation_vendor_id",
                        unique = true
                ),
                @Index(
                        name = "idx_external_vendor_ledger",
                        columnList = "ledger_id",
                        unique = true
                ),
                @Index(
                        name = "idx_external_vendor_gst",
                        columnList = "gst_number"
                ),
                @Index(
                        name = "idx_external_vendor_pan",
                        columnList = "pan_number"
                ),
                @Index(
                        name = "idx_external_vendor_active",
                        columnList = "active"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Vendor ID received from Operation Service.
     *
     * This is the main idempotency key.
     * One Operation Service vendor must have only one Account Service record.
     */
    @Column(
            name = "operation_vendor_id",
            nullable = false,
            unique = true
    )
    private Long operationVendorId;

    /*
     * Accounts submission ID from Operation Service.
     */
    @Column(name = "vendor_accounts_submission_id")
    private Long vendorAccountsSubmissionId;

    /*
     * Vendor finalization ID from Operation Service.
     */
    @Column(name = "vendor_finalization_id")
    private Long vendorFinalizationId;

    /*
     * Vendor ledger created under Sundry Creditors.
     *
     * Ledger Type  : VENDOR
     * Ledger Group : SUNDRY_CREDITORS
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "ledger_id",
            nullable = false,
            unique = true
    )
    private LedgerMaster ledger;

    // =====================================================
    // VENDOR BASIC DETAILS
    // =====================================================

    @Column(
            name = "vendor_name",
            nullable = false,
            length = 255
    )
    private String vendorName;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String mobile;

    // =====================================================
    // TAX DETAILS
    // =====================================================

    @Column(
            name = "pan_number",
            length = 10
    )
    private String panNumber;

    @Column(
            name = "gst_number",
            length = 15
    )
    private String gstNumber;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "gst_registration_type",
            length = 50
    )
    private GstRegistrationType gstRegistrationType;

    // =====================================================
    // BANK DETAILS
    // =====================================================

    @Column(
            name = "account_holder_name",
            length = 255
    )
    private String accountHolderName;

    @Column(
            name = "bank_account_number",
            length = 100
    )
    private String bankAccountNumber;

    @Column(
            name = "ifsc_code",
            length = 20
    )
    private String ifscCode;

    @Column(
            name = "bank_name",
            length = 255
    )
    private String bankName;

    @Column(
            name = "branch_address",
            length = 1000
    )
    private String branchAddress;

    // =====================================================
    // ADDRESS DETAILS
    // =====================================================

    @Column(
            name = "full_address",
            length = 1000
    )
    private String fullAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String country;

    // =====================================================
    // APPROVAL DETAILS FROM OPERATION SERVICE
    // =====================================================

    @Column(name = "approved_by_operation_user_id")
    private Long approvedByOperationUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /*
     * Last update timestamp received from Operation Service.
     */
    @Column(name = "operation_updated_at")
    private LocalDateTime operationUpdatedAt;

    /*
     * Last successful synchronization timestamp.
     */
    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    // =====================================================
    // STATUS
    // =====================================================

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted = false;

    // =====================================================
    // AUDIT FIELDS
    // =====================================================

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "tds_applicable", nullable = false)
    private boolean tdsApplicable = false;

    @Column(name = "tds_section", length = 20)
    private String tdsSection;

    @Column(name = "default_tds_percentage", precision = 5, scale = 2)
    private BigDecimal defaultTdsPercentage;

    @Column(name = "lower_deduction_certificate_number", length = 100)
    private String lowerDeductionCertificateNumber;

    @Column(name = "lower_tds_percentage", precision = 5, scale = 2)
    private BigDecimal lowerTdsPercentage;

    @PrePersist
    public void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.lastSyncedAt == null) {
            this.lastSyncedAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {

        this.updatedAt = LocalDateTime.now();
        this.lastSyncedAt = LocalDateTime.now();
    }
}