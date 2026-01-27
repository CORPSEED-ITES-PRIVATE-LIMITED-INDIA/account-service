package com.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "company_unit",
        uniqueConstraints = {
                // ✅ unit name unique within a company
                @UniqueConstraint(columnNames = {"company_id", "unit_name"}),
                // ✅ GST unique within a company (change to global unique if you want)
                @UniqueConstraint(columnNames = {"company_id", "gst_no"})
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class CompanyUnit {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    // Unit identity
    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(name = "lead_id")
    private Long leadId;

    // Address
    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;


    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state", nullable = false)
    private String state;

    @Column(name = "country", nullable = false)
    private String country = "India";

    @Column(name = "pin_code", nullable = false)
    private String pinCode;

    // GST (unit-level)
    @Column(name = "gst_no", length = 15)
    private String gstNo;

    @Column(name = "gst_type")
    private String gstType;

    @Column(name = "gst_documents", columnDefinition = "TEXT")
    private String gstDocuments;

    @Column(name = "gst_type_entity")
    private String gstTypeEntity;

    @Column(name = "gst_business_type")
    private String gstBusinessType;

    @Column(name = "gst_type_price")
    private String gstTypePrice;

    // Contacts
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_contact_id")
    private Contact primaryContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secondary_contact_id")
    private Contact secondaryContact;

    // Link to Company
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Unit lifecycle
    @Column(name = "unit_opening_date")
    private LocalDate unitOpeningDate;

    @Column(name = "status", nullable = false)
    private String status = "Active";

    @Column(name = "consultant_present", nullable = false)
    private boolean consultantPresent = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    // Auditing
    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_id")
    private User updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    @Column(name = "accounts_approved", nullable = false)
    private boolean accountsApproved = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accounts_reviewed_by_id")
    private User accountsReviewedBy;

    @Column(name = "accounts_reviewed_at")
    private LocalDateTime accountsReviewedAt;

    @Column(name = "accounts_remark", columnDefinition = "TEXT")
    private String accountsRemark;
    @PrePersist
    protected void onCreateDefaults() {
        if (status == null) status = "Active";
        if (country == null) country = "India";
        // isDeleted default false already
    }
}
