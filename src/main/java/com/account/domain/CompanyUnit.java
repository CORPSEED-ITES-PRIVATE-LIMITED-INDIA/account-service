package com.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Table(name = "company_unit")
@Getter
@Setter
public class CompanyUnit {

    @Id
    @Column(name = "id", updatable = false, insertable = true)
    private Long id;

    @Column(name = "unit_name")
    private String unitName;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    private String city;
    private String state;
    private String country = "India";
    private String pinCode;

    @Column(name = "gst_no")
    private String gstNo;

    @Column(name = "gst_type")
    private String gstType;

    @Column(name = "gst_documents")
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

    // Bidirectional link to Company
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "unit_opening_date")
    private Date unitOpeningDate;

    @Column(name = "status")
    private String status = "Active";

    @Column(name = "consultant_present")
    private boolean consultantPresent = false;

    @Column(name = "is_deleted")
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
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        isDeleted = false;
    }
}