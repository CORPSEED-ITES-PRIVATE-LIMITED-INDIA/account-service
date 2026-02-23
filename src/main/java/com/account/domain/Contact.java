package com.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "contact",
        indexes = {
                @Index(name = "idx_contact_name", columnList = "name"),
                @Index(name = "idx_contact_contact_no", columnList = "contact_no"),
                @Index(name = "idx_contact_whatsapp_no", columnList = "whatsapp_no"),
                @Index(name = "idx_contact_is_deleted", columnList = "is_deleted")
        })
@Getter
@Setter
public class Contact {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    private String title;

    private String name;

    private String emails;

    @Column(name = "contact_no")
    private String contactNo;

    @Column(name = "whatsapp_no")
    private String whatsappNo;

    private String clientDesignation;

    private String designation;

    private boolean deleteStatus = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_unit_id")
    private CompanyUnit companyUnit;

    private boolean isPrimaryForCompany = false;
    private boolean isSecondaryForCompany = false;
    private boolean isPrimaryForUnit = false;
    private boolean isSecondaryForUnit = false;

    // Helper methods for clean assignment
    public void assignAsPrimaryToCompany(Company company) {
        this.company = company;
        this.isPrimaryForCompany = true;
        this.isSecondaryForCompany = false;
    }

    public void assignAsSecondaryToCompany(Company company) {
        this.company = company;
        this.isPrimaryForCompany = false;
        this.isSecondaryForCompany = true;
    }

    public void assignAsPrimaryToUnit(CompanyUnit unit) {
        this.companyUnit = unit;
        this.isPrimaryForUnit = true;
        this.isSecondaryForUnit = false;
    }

    public void assignAsSecondaryToUnit(CompanyUnit unit) {
        this.companyUnit = unit;
        this.isPrimaryForUnit = false;
        this.isSecondaryForUnit = true;
    }
}