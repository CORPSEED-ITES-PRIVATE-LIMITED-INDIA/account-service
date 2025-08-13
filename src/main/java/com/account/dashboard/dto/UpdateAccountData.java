package com.account.dashboard.dto;

import java.util.Date;

public class UpdateAccountData {

	private Long currentUserId;
	private String transactionId;
	private String serviceName;  // need to Discussion
	private String professionalFees;
	private String govFees;
	private String serviceCharge;
	private String othertherFees;
	private String UploadReceipt;
	private String totalAmount;
	private String remark;
	private Date paymentDate;
	private Long approvedById;
	private Date approveDate;
	
	public Long getCurrentUserId() {
		return currentUserId;
	}
	public void setCurrentUserId(Long currentUserId) {
		this.currentUserId = currentUserId;
	}
	public String getTransactionId() {
		return transactionId;
	}
	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public String getProfessionalFees() {
		return professionalFees;
	}
	public void setProfessionalFees(String professionalFees) {
		this.professionalFees = professionalFees;
	}
	public String getGovFees() {
		return govFees;
	}
	public void setGovFees(String govFees) {
		this.govFees = govFees;
	}
	public String getServiceCharge() {
		return serviceCharge;
	}
	public void setServiceCharge(String serviceCharge) {
		this.serviceCharge = serviceCharge;
	}
	public String getOthertherFees() {
		return othertherFees;
	}
	public void setOthertherFees(String othertherFees) {
		this.othertherFees = othertherFees;
	}
	public String getUploadReceipt() {
		return UploadReceipt;
	}
	public void setUploadReceipt(String uploadReceipt) {
		UploadReceipt = uploadReceipt;
	}
	public String getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(String totalAmount) {
		this.totalAmount = totalAmount;
	}
	public String getRemark() {
		return remark;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	public Date getPaymentDate() {
		return paymentDate;
	}
	public void setPaymentDate(Date paymentDate) {
		this.paymentDate = paymentDate;
	}
	public Long getApprovedById() {
		return approvedById;
	}
	public void setApprovedById(Long approvedById) {
		this.approvedById = approvedById;
	}
	public Date getApproveDate() {
		return approveDate;
	}
	public void setApproveDate(Date approveDate) {
		this.approveDate = approveDate;
	}
	
	
}
