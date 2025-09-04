package com.account.dashboard.dto;

import java.util.Date;
import java.util.List;

public class CreatePurchaseOrderDto {
	
	String registerType;// payment , purchase order
	String purchaseNumber;
	Date purchaseDate;
	List<String> purchaseAttach;
	String paymentTerm;
	String comment;
	String updateDate;
	private Long approvedById;
	private Date approveDate;
	private Long leadId;
	private Long estimateId;
	private Long createdById;
	private String serviceName;

	public String getRegisterType() {
		return registerType;
	}
	public String getPurchaseNumber() {
		return purchaseNumber;
	}
	public Date getPurchaseDate() {
		return purchaseDate;
	}

	public String getPaymentTerm() {
		return paymentTerm;
	}
	public String getComment() {
		return comment;
	}
	public String getUpdateDate() {
		return updateDate;
	}
	public Long getApprovedById() {
		return approvedById;
	}
	public Date getApproveDate() {
		return approveDate;
	}
	public Long getLeadId() {
		return leadId;
	}
	public Long getEstimateId() {
		return estimateId;
	}
	public Long getCreatedById() {
		return createdById;
	}
	public void setRegisterType(String registerType) {
		this.registerType = registerType;
	}
	public void setPurchaseNumber(String purchaseNumber) {
		this.purchaseNumber = purchaseNumber;
	}
	public void setPurchaseDate(Date purchaseDate) {
		this.purchaseDate = purchaseDate;
	}

	public void setPaymentTerm(String paymentTerm) {
		this.paymentTerm = paymentTerm;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public void setUpdateDate(String updateDate) {
		this.updateDate = updateDate;
	}
	public void setApprovedById(Long approvedById) {
		this.approvedById = approvedById;
	}
	public void setApproveDate(Date approveDate) {
		this.approveDate = approveDate;
	}
	public void setLeadId(Long leadId) {
		this.leadId = leadId;
	}
	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}
	public void setCreatedById(Long createdById) {
		this.createdById = createdById;
	}
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public List<String> getPurchaseAttach() {
		return purchaseAttach;
	}
	public void setPurchaseAttach(List<String> purchaseAttach) {
		this.purchaseAttach = purchaseAttach;
	}
	
	

}
