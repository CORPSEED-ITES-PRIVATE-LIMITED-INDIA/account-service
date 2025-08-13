package com.account.dashboard.dto;

public class CreateStatutoryDetails {
	Long id;
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
	public Long getId() {
		return id;
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
	public void setId(Long id) {
		this.id = id;
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
	
	
	
	
	
}
