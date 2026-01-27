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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
		name = "company",
		uniqueConstraints = {
				@UniqueConstraint(columnNames = {"pan_no"})
		}
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Company {

	@Id
	@Column(name = "id", updatable = false, nullable = false)
	private Long id;

	@Column(name = "uuid", unique = true, length = 36)
	private String uuid;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "pan_no", nullable = false, length = 10, unique = true)
	private String panNo;

	@Column(name = "establish_date")
	private LocalDate establishDate;

	@Column(name = "industry")
	private String industry;

	@Column(name = "industries")
	private String industries;

	@Column(name = "sub_industry")
	private String subIndustry;

	@Column(name = "subsub_industry")
	private String subsubIndustry;

	@Column(name = "lead_id")
	private Long leadId;

	@Column(name = "status")
	private String status;

	// Consultant Flow
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Company parent;

	@Column(name = "is_consultant", nullable = false)
	private Boolean isConsultant = false;

	// Agreements
	@Column(name = "payment_term")
	private String paymentTerm;

	@Column(name = "agreement_present", nullable = false)
	private boolean aggrementPresent = false;

	@Column(name = "agreement", columnDefinition = "TEXT")
	private String aggrement;

	@Column(name = "nda_present", nullable = false)
	private boolean ndaPresent = false;

	@Column(name = "nda", columnDefinition = "TEXT")
	private String nda;

	@Column(name = "revenue")
	private String revenue;

	// Relationship: Company -> Units
	@OneToMany(
			mappedBy = "company",
			cascade = CascadeType.ALL,
			orphanRemoval = true,
			fetch = FetchType.LAZY
	)
	private List<CompanyUnit> units = new ArrayList<>();

	// Onboarding & Accounts approval
	@Column(name = "onboarding_status")
	private String onboardingStatus;

	@Column(name = "accounts_approved", nullable = false)
	private boolean accountsApproved = false;

	@Column(name = "accounts_remark", columnDefinition = "TEXT")
	private String accountsRemark;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "accounts_reviewed_by_id")
	private User accountsReviewedBy;

	@Column(name = "accounts_reviewed_at")
	private LocalDateTime accountsReviewedAt;

	// Soft delete
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

	// ✅ Helper methods (important for bidirectional + orphanRemoval)
	public void addUnit(CompanyUnit unit) {
		if (unit == null) return;
		units.add(unit);
		unit.setCompany(this);
	}

	public void removeUnit(CompanyUnit unit) {
		if (unit == null) return;
		units.remove(unit);
		unit.setCompany(null);
	}

	@PrePersist
	protected void onCreateDefaults() {
		if (status == null) status = "Active";
		// isDeleted default false already
		if (isConsultant == null) isConsultant = false;
	}
}
