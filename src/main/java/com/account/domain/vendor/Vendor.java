package com.account.domain.vendor;

import com.account.enm.VendorGSTRegistrationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "vendors",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_account_vendor_operation_vendor",
                        columnNames = "operation_vendor_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_vendor_operation_vendor_id",
                        columnList = "operation_vendor_id"
                ),
                @Index(
                        name = "idx_vendor_gst_number",
                        columnList = "gst_number"
                ),
                @Index(
                        name = "idx_vendor_pan",
                        columnList = "pan"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Primary vendor ID from Operation Service.
     */
    @Column(name = "operation_vendor_id", nullable = false, updatable = false)
    private Long operationVendorId;

    @Column(name = "vendor_name", nullable = false, length = 255)
    private String vendorName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "pan", length = 10)
    private String pan;

    @Column(name = "gst_number", length = 15)
    private String gstNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_registration_type", length = 30)
    private VendorGSTRegistrationType gstRegistrationType;

    @Column(name = "account_holder_name", length = 255)
    private String accountHolderName;

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    @Column(name = "ifsc_code", length = 20)
    private String ifscCode;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    /**
     * Last modification time received from Operation Service.
     */
    @Column(name = "operation_updated_at")
    private LocalDateTime operationUpdatedAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    @PreUpdate
    private void updateSyncTime() {
        this.syncedAt = LocalDateTime.now();

        if (this.active == null) {
            this.active = true;
        }
    }
}