package com.account.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class OrganizationRequestDto {

	@NotBlank(message = "Organization name is required")
	@Size(max = 255)
	private String name;

	@Size(max = 255)
	private String addressLine1;

	@Size(max = 255)
	private String addressLine2;

	@Size(max = 100)
	private String city;

	@Size(max = 100)
	private String state;

	@Size(max = 100)
	private String country;

	@Size(max = 20)
	private String pinCode;

	@Size(max = 50)
	private String gstNo;

	@Size(max = 50)
	private String panNo;

	@Size(max = 21)
	private String cinNumber;

	private LocalDate establishedDate;

	@Size(max = 255)
	private String ownerName;

	private boolean bankAccountPresent;

	@Size(max = 255)
	private String accountHolderName;

	@Size(max = 100)
	private String accountNo;

	@Size(max = 20)
	private String ifscCode;

	@Size(max = 20)
	private String swiftCode;

	@Size(max = 100)
	private String bankName;

	@Size(max = 100)
	private String branch;

	@Size(max = 100)
	private String upiId;

	@Size(max = 500)
	private String website;

	@Size(max = 500)
	private String paymentPageLink;

	private String estimateConditions;

	@Size(max = 255)
	private String logoUrl;

	@Email
	@Size(max = 100)
	private String email;

	@Size(max = 50)
	private String phone;

	private boolean active = true;
}