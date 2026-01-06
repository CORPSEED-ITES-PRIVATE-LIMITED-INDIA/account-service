package com.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "company")
@Getter
@Setter
public class Company {

	@Id
	@Column(name = "id", updatable = false, insertable = true)
	private Long id;


	private String uuid;

	@Column(name = "name")
	private String name;

	@Column(name = "pan_no", unique = true)
	private String panNo;

	private String gstType;
	private String gstNo;
	private String gstDocuments;

	private String companyGstType;

	private String gstBussinessType;

	private String gstTypePrice;

	private Date establishDate;
	private String industry;

	// Legacy Primary Address (kept for backward compatibility)
	private String Address;
	private String City;
	private String State;
	private String Country;
	private String primaryPinCode;

	// Legacy Secondary Address
	private String sAddress;
	private String sCity;
	private String sState;
	private String sCountry;
	private String secondaryPinCode;


	private Long leadId;

	private String status;

	// Consultant Flow
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Company parent;

	private Boolean isConsultant = false;

	private String industries;

	private String subIndustry;

	private String subsubIndustry;


	// Agreements
	private String paymentTerm;
	private boolean aggrementPresent = false;
	private String aggrement;
	private String nda;
	private boolean ndaPresent = false;
	private String revenue;

	// Audit
	private boolean isDeleted = false;
	private Date createDate;
	private LocalDate date;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_id")
	private User createdBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by_id")
	private User updatedBy;

	private Date updateDate;

	// === NEW: OneToMany relationship with CompanyUnit ===
	@OneToMany(
			mappedBy = "company",
			cascade = CascadeType.ALL,
			orphanRemoval = true,
			fetch = FetchType.LAZY
	)
	private List<CompanyUnit> units = new ArrayList<>();

	// === ONBOARDING & ACCOUNTS APPROVAL STATUS ===
	@Column(name = "onboarding_status")
	private String onboardingStatus;

	// Possible values: "Approved", "Rejected",
	@Column(name = "accounts_approved")
	private boolean accountsApproved = false;

	@Column(name = "accounts_remark", columnDefinition = "TEXT")
	private String accountsRemark;  // Long text for comments

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "accounts_reviewed_by_id")
	private User accountsReviewedBy;

	@Column(name = "accounts_reviewed_at")
	private LocalDateTime accountsReviewedAt;

	@PrePersist
	protected void onCreate() {
		if (createDate == null) {
			createDate = new Date();
		}
		isDeleted = false;
	}
}