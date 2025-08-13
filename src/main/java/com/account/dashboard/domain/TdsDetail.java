package com.account.dashboard.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
@Table
@Entity
public class TdsDetail {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	String tdsType; // payable,reciveable
	String organization;
	double totalPaymentAmount;
	int tdsPrecent;
	Long projectId;
	String paymentRegisterId;
	double tdsAmount;
	
	public Long getId() {
		return id;
	}
	public String getOrganization() {
		return organization;
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

	public void setId(Long id) {
		this.id = id;
	}
	public void setOrganization(String organization) {
		this.organization = organization;
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
	public String getTdsType() {
		return tdsType;
	}
	public double getTotalPaymentAmount() {
		return totalPaymentAmount;
	}
	public double getTdsAmount() {
		return tdsAmount;
	}
	public void setTdsType(String tdsType) {
		this.tdsType = tdsType;
	}
	public void setTotalPaymentAmount(double totalPaymentAmount) {
		this.totalPaymentAmount = totalPaymentAmount;
	}
	public void setTdsAmount(double tdsAmount) {
		this.tdsAmount = tdsAmount;
	}
     
	
	
	
}
