package com.account.dashboard.dto;

public class BankAccountDto {
	
	
    private Long bankAccountId;	
	boolean bankAccountPresent;
	String accountHolderName;
	String accountNo;
	String ifscCode;
	String swiftCode;
	String bankName;
	String branch;
	public Long getBankAccountId() {
		return bankAccountId;
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
	public void setBankAccountId(Long bankAccountId) {
		this.bankAccountId = bankAccountId;
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
