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
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public LedgerType getLedgerType() {
		return ledgerType;
	}
	public void setLedgerType(LedgerType ledgerType) {
		this.ledgerType = ledgerType;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	public Long getEstimateId() {
		return estimateId;
	}
	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}
	public Long getCompanyId() {
		return companyId;
	}
	public void setCompanyId(Long companyId) {
		this.companyId = companyId;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getAddress() {
		return address;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public String getState() {
		return state;
	}
	public void setState(String state) {
		this.state = state;
	}
	public String getCountry() {
		return country;
	}
	public void setCountry(String country) {
		this.country = country;
	}
	public String getPin() {
		return pin;
	}
	public void setPin(String pin) {
		this.pin = pin;
	}
	public Date getCreateDate() {
		return createDate;
	}
	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
	public boolean isDeleted() {
		return isDeleted;
	}
	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}
	public boolean isHsnSacPrsent() {
		return hsnSacPrsent;
	}
	public void setHsnSacPrsent(boolean hsnSacPrsent) {
		this.hsnSacPrsent = hsnSacPrsent;
	}
	public String getHsnSacDetails() {
		return hsnSacDetails;
	}
	public void setHsnSacDetails(String hsnSacDetails) {
		this.hsnSacDetails = hsnSacDetails;
	}
	public String getHsnSacName() {
		return hsnSacName;
	}
	public void setHsnSacName(String hsnSacName) {
		this.hsnSacName = hsnSacName;
	}
	public String getHsnDescription() {
		return hsnDescription;
	}
	public void setHsnDescription(String hsnDescription) {
		this.hsnDescription = hsnDescription;
	}
	public boolean isGstRateDetailPresent() {
		return gstRateDetailPresent;
	}
	public void setGstRateDetailPresent(boolean gstRateDetailPresent) {
		this.gstRateDetailPresent = gstRateDetailPresent;
	}
	public String getGstRateDetails() {
		return gstRateDetails;
	}
	public void setGstRateDetails(String gstRateDetails) {
		this.gstRateDetails = gstRateDetails;
	}
	public String getTaxabilityType() {
		return taxabilityType;
	}
	public void setTaxabilityType(String taxabilityType) {
		this.taxabilityType = taxabilityType;
	}
	public String getGstRates() {
		return gstRates;
	}
	public void setGstRates(String gstRates) {
		this.gstRates = gstRates;
	}
	public boolean isCgstSgstPresent() {
		return cgstSgstPresent;
	}
	public void setCgstSgstPresent(boolean cgstSgstPresent) {
		this.cgstSgstPresent = cgstSgstPresent;
	}
	public boolean isIgstPresent() {
		return igstPresent;
	}
	public void setIgstPresent(boolean igstPresent) {
		this.igstPresent = igstPresent;
	}
	public String getIgst() {
		return igst;
	}
	public void setIgst(String igst) {
		this.igst = igst;
	}
	public String getCgst() {
		return cgst;
	}
	public void setCgst(String cgst) {
		this.cgst = cgst;
	}
	public String getSgst() {
		return sgst;
	}
	public void setSgst(String sgst) {
		this.sgst = sgst;
	}
	public boolean isBankAccountPrsent() {
		return bankAccountPrsent;
	}
	public void setBankAccountPrsent(boolean bankAccountPrsent) {
		this.bankAccountPrsent = bankAccountPrsent;
	}
	public String getAccount() {
		return account;
	}
	public void setAccount(String account) {
		this.account = account;
	}
	public String getAccountHolderName() {
		return accountHolderName;
	}
	public void setAccountHolderName(String accountHolderName) {
		this.accountHolderName = accountHolderName;
	}
	public String getIfscCode() {
		return ifscCode;
	}
	public void setIfscCode(String ifscCode) {
		this.ifscCode = ifscCode;
	}
	public String getSwiftCode() {
		return swiftCode;
	}
	public void setSwiftCode(String swiftCode) {
		this.swiftCode = swiftCode;
	}
	public String getBankName() {
		return bankName;
	}
	public void setBankName(String bankName) {
		this.bankName = bankName;
	}
	public String getBranch() {
		return branch;
	}
	public void setBranch(String branch) {
		this.branch = branch;
	}
	
	
}
