package com.account.dto;

import java.util.Date;

import lombok.Data;

//@Data
public class CreateTdsDto {
	
	String organization;
	double totalPaymentAmount;
	String tdsType; // payable,reciveable
	int tdsPrecent;
	Long projectId;
	String paymentRegisterId;
	double tdsAmount;
	Long ledgerId;
	//claim
	double tdsClaimAmount;
	Date claimDate;
	String documents;
	
	public String getOrganization() {
		return organization;
	}
	public double getTotalPaymentAmount() {
		return totalPaymentAmount;
	}
	public String getTdsType() {
		return tdsType;
	}
	public int getTdsPrecent() {
		return tdsPrecent;
	}
	public Long getProjectId() {
		return projectId;
	}
	public String getPaymentRegisterId() {
		return paymentRegisterId;
	}
	public double getTdsAmount() {
		return tdsAmount;
	}
	public void setOrganization(String organization) {
		this.organization = organization;
	}
	public void setTotalPaymentAmount(double totalPaymentAmount) {
		this.totalPaymentAmount = totalPaymentAmount;
	}
	public void setTdsType(String tdsType) {
		this.tdsType = tdsType;
	}
	public void setTdsPrecent(int tdsPrecent) {
		this.tdsPrecent = tdsPrecent;
	}
	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}
	public void setPaymentRegisterId(String paymentRegisterId) {
		this.paymentRegisterId = paymentRegisterId;
	}
	public void setTdsAmount(double tdsAmount) {
		this.tdsAmount = tdsAmount;
	}
	public Long getLedgerId() {
		return ledgerId;
	}
	public void setLedgerId(Long ledgerId) {
		this.ledgerId = ledgerId;
	}
	public double getTdsClaimAmount() {
		return tdsClaimAmount;
	}
	public void setTdsClaimAmount(double tdsClaimAmount) {
		this.tdsClaimAmount = tdsClaimAmount;
	}
	public Date getClaimDate() {
		return claimDate;
	}
	public void setClaimDate(Date claimDate) {
		this.claimDate = claimDate;
	}
	public String getDocuments() {
		return documents;
	}
	public void setDocuments(String documents) {
		this.documents = documents;
	}
	
	
	
}
