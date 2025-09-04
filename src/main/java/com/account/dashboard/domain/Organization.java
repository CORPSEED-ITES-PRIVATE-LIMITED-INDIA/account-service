package com.account.dashboard.domain;

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
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Table
@Entity
//@Getter
//@Setter
//@Data
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

	public Long getId() {
		return id;
	}
	public String getName() {
		return name;
	}
	public Date getJoiningDate() {
		return joiningDate;
	}
	public boolean isDeleted() {
		return isDeleted;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public void setName(String name) {
		this.name = name;
	}
	public void setJoiningDate(Date joiningDate) {
		this.joiningDate = joiningDate;
	}
	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}
	public String getAddress() {
		return address;
	}
	public String getState() {
		return state;
	}
	public String getCountry() {
		return country;
	}
	public String getPin() {
		return pin;
	}
	public Date getCreateDate() {
		return createDate;
	}
	public void setAddress(String address) {
		this.address = address;
	}
	public void setState(String state) {
		this.state = state;
	}
	public void setCountry(String country) {
		this.country = country;
	}
	public void setPin(String pin) {
		this.pin = pin;
	}
	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
	public boolean isHsnSacPresent() {
		return hsnSacPresent;
	}
	public String getHsnSacDetails() {
		return hsnSacDetails;
	}
	public String getClassification() {
		return classification;
	}
	public String getHsnSacData() {
		return hsnSacData;
	}
	public String getHsnDescription() {
		return hsnDescription;
	}
	public boolean isGstRateDetailPresent() {
		return gstRateDetailPresent;
	}
	public String getGstRateDetails() {
		return gstRateDetails;
	}
	public String getTaxabilityType() {
		return taxabilityType;
	}
	public String getGstRatesData() {
		return gstRatesData;
	}
	public boolean isBankAccountPresent() {
		return bankAccountPresent;
	}
	public String getAccountHolderName() {
		return accountHolderName;
	}
	public String getAccountNo() {
		return accountNo;
	}
	public String getIfscCode() {
		return ifscCode;
	}
	public String getSwiftCode() {
		return swiftCode;
	}
	public String getBankName() {
		return bankName;
	}
	public String getBranch() {
		return branch;
	}
	public void setHsnSacPresent(boolean hsnSacPresent) {
		this.hsnSacPresent = hsnSacPresent;
	}
	public void setHsnSacDetails(String hsnSacDetails) {
		this.hsnSacDetails = hsnSacDetails;
	}
	public void setClassification(String classification) {
		this.classification = classification;
	}
	public void setHsnSacData(String hsnSacData) {
		this.hsnSacData = hsnSacData;
	}
	public void setHsnDescription(String hsnDescription) {
		this.hsnDescription = hsnDescription;
	}
	public void setGstRateDetailPresent(boolean gstRateDetailPresent) {
		this.gstRateDetailPresent = gstRateDetailPresent;
	}
	public void setGstRateDetails(String gstRateDetails) {
		this.gstRateDetails = gstRateDetails;
	}
	public void setTaxabilityType(String taxabilityType) {
		this.taxabilityType = taxabilityType;
	}
	public void setGstRatesData(String gstRatesData) {
		this.gstRatesData = gstRatesData;
	}
	public void setBankAccountPresent(boolean bankAccountPresent) {
		this.bankAccountPresent = bankAccountPresent;
	}
	public void setAccountHolderName(String accountHolderName) {
		this.accountHolderName = accountHolderName;
	}
	public void setAccountNo(String accountNo) {
		this.accountNo = accountNo;
	}
	public void setIfscCode(String ifscCode) {
		this.ifscCode = ifscCode;
	}
	public void setSwiftCode(String swiftCode) {
		this.swiftCode = swiftCode;
	}
	public void setBankName(String bankName) {
		this.bankName = bankName;
	}
	public void setBranch(String branch) {
		this.branch = branch;
	}
	public List<BankAccount> getOrganizationBankAccount() {
		return organizationBankAccount;
	}
	public void setOrganizationBankAccount(List<BankAccount> organizationBankAccount) {
		this.organizationBankAccount = organizationBankAccount;
	}
	
	
}
