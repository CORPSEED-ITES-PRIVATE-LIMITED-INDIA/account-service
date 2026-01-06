package com.account.domain;

import java.util.Date;
import java.util.List;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Table
@Entity
@Getter
@Setter
public class Organization {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	private String name;
	
	Date joiningDate;
	boolean isDeleted ;
	
	String address;	
	String state;	
	String country;	
	String pin;	
	Date createDate; 
	
	//hsn details
	boolean hsnSacPresent;
	String hsnSacDetails;
	String classification;
	String hsnSacData;
	String hsnDescription;



	// gst rate details
	boolean gstRateDetailPresent;
	String gstRateDetails;
	String taxabilityType;
	String gstRatesData;


	//Bank Details

	boolean bankAccountPresent;
	String accountHolderName;
	String accountNo;
	String ifscCode;
	String swiftCode;
	String bankName;
	String branch;
	
	
	@ManyToMany(cascade = CascadeType.ALL)
	@JoinTable(name="organization_bank_account",joinColumns = {@JoinColumn(name="organization_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="organization_bank_account_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
	List<BankAccount>organizationBankAccount;


	
}
