package com.account.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "organization")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

	@Id
	private Long id = 1L; // Singleton - always ID=1 for Corpseed

	@Column(nullable = false, length = 255)
	private String name;

	@Column(length = 255)
	private String addressLine1;

	@Column(length = 255)
	private String addressLine2;

	@Column(length = 100)
	private String city;

	@Column(length = 100)
	private String state;

	@Column(length = 100)
	private String country = "India";

	@Column(length = 20)
	private String pinCode;

	@Column(length = 50)
	private String gstNo;

	@Column(length = 50)
	private String panNo;

	@Column(length = 21)
	private String cinNumber;

	@Column
	private LocalDate establishedDate;

	@Column(length = 255)
	private String ownerName;

	// Bank Details
	@Column
	private boolean bankAccountPresent = false;

	@Column(length = 255)
	private String accountHolderName;

	@Column(length = 100)
	private String accountNo;

	@Column(length = 20)
	private String ifscCode;

	@Column(length = 20)
	private String swiftCode;

	@Column(length = 100)
	private String bankName;

	@Column(length = 100)
	private String branch;

	// UPI & Payment Details
	@Column(length = 100)
	private String upiId;

	@Column(length = 500)
	private String website;

	@Column(length = 500)
	private String paymentPageLink;

	// Conditions – only shown on Estimates
	@Column(columnDefinition = "TEXT")
	private String estimateConditions;

	@Column(length = 255)
	private String logoUrl;

	@Column(length = 100)
	private String email;

	@Column(length = 50)
	private String phone;

	private boolean active = true;

	@CreatedDate
	@Column(updatable = false)
	private LocalDateTime createdAt;

	@LastModifiedDate
	private LocalDateTime updatedAt;

	@CreatedBy
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private User createdBy;

	@LastModifiedBy
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by")
	private User updatedBy;

	@PrePersist
	protected void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}