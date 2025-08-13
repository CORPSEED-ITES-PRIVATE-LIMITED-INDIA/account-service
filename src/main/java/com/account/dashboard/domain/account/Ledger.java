package com.account.dashboard.domain.account;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Table
@Entity
@Getter
@Setter
@Data
public class Ledger {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	
	String name;
	@ManyToOne
	LedgerType ledgerType;
	
	String source;//sales
	Long estimateId;
	Long companyId;
	
	
	String email;
	
	String address;	
	String state;	
	String country;	
	String pin;	
	Date createDate;
	
	boolean isDeleted;
	
	//hsn details
    boolean hsnSacPrsent;
	String hsnSacDetails;
	 @Column(name = "hsn_sac_name")
	String hsnSacName;
	String hsnDescription;


	// gst rate details
	boolean gstRateDetailPresent;
	String gstRateDetails;
	String taxabilityType;
	String gstRates;
	boolean cgstSgstPresent;
	boolean igstPresent;

	String igst; //other state
	String cgst; // both are same state (cgst+sgst)
	String sgst; // both are same state (cgst+sgst)
	
	// BankDetails
	boolean bankAccountPrsent;
	
//	String accountHolderName;
	@Column(name = "account")
	String account;
	String accountHolderName;
	String ifscCode;
	String swiftCode;
	String bankName;
	String branch;
	
	
}
