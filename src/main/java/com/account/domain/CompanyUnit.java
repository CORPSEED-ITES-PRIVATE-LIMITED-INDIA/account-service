package com.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "company_unit")
@EntityListeners(AuditingEntityListener.class)   // ← important for @CreatedDate etc.
@Getter
@Setter
public class CompanyUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // auto-increment in MySQL/PostgreSQL
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "unit_name", nullable = false)
    private String unitName;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;
    private String state;

    @Column(nullable = false)
    private String country = "India";

    @Column(name = "pin_code")
    private String pinCode;

    @Column(name = "gst_no", length = 15)
    private String gstNo;

    @Column(name = "gst_type")
    private String gstType;

    @Column(name = "gst_documents", columnDefinition = "TEXT")
    private String gstDocuments;

    private String gstTypeEntity;
    private String gstBusinessType;
    private String gstTypePrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_contact_id")
    private Contact primaryContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "secondary_contact_id")
    private Contact secondaryContact;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "unit_opening_date")
    @Temporal(TemporalType.DATE)
    private Date unitOpeningDate;

    @Column(nullable = false)
    private String status = "Active";

    private boolean consultantPresent = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    // Auditing fields
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

    @PrePersist
    protected void onCreate() {
        if (isDeleted == false) {   // avoid null → default false
            isDeleted = false;
        }
        if (status == null) {
            status = "Active";
        }
        if (country == null) {
            country = "India";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        // optional: can add logic here if needed
    }
}