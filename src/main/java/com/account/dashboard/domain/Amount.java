package com.account.dashboard.domain;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

//@Data
@Entity
@Table
public class Amount {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	String transactionType;
	@ManyToOne
	User createdBy;
	List<String> transactionId;
	String serviceName;  // need to Discussion
//==================	
    double  govermentfees;
	double govermentGst;
	
	double professionalFees;
	double profesionalGst;
	
	double serviceCharge;
	double serviceGst;
	
	double otherFees;
	double otherGst;
	
	String UploadReceipt;
	double totalAmount;
	String remark;
	Date paymentDate;
	List<String>doc;
	@ManyToOne
	User approvedBy;
	Date approveDate;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getTransactionType() {
		return transactionType;
	}
	public void setTransactionType(String transactionType) {
		this.transactionType = transactionType;
	}
	public User getCreatedBy() {
		return createdBy;
	}
	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
	}
	public List<String> getTransactionId() {
		return transactionId;
	}
	public void setTransactionId(List<String> transactionId) {
		this.transactionId = transactionId;
	}
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public double getGovermentfees() {
		return govermentfees;
	}
	public void setGovermentfees(double govermentfees) {
		this.govermentfees = govermentfees;
	}
	public double getGovermentGst() {
		return govermentGst;
	}
	public void setGovermentGst(double govermentGst) {
		this.govermentGst = govermentGst;
	}
	public double getProfessionalFees() {
		return professionalFees;
	}
	public void setProfessionalFees(double professionalFees) {
		this.professionalFees = professionalFees;
	}
	public double getProfesionalGst() {
		return profesionalGst;
	}
	public void setProfesionalGst(double profesionalGst) {
		this.profesionalGst = profesionalGst;
	}
	public double getServiceCharge() {
		return serviceCharge;
	}
	public void setServiceCharge(double serviceCharge) {
		this.serviceCharge = serviceCharge;
	}
	public double getServiceGst() {
		return serviceGst;
	}
	public void setServiceGst(double serviceGst) {
		this.serviceGst = serviceGst;
	}
	public double getOtherFees() {
		return otherFees;
	}
	public void setOtherFees(double otherFees) {
		this.otherFees = otherFees;
	}
	public double getOtherGst() {
		return otherGst;
	}
	public void setOtherGst(double otherGst) {
		this.otherGst = otherGst;
	}
	public String getUploadReceipt() {
		return UploadReceipt;
	}
	public void setUploadReceipt(String uploadReceipt) {
		UploadReceipt = uploadReceipt;
	}
	public double getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(double totalAmount) {
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
	public List<String> getDoc() {
		return doc;
	}
	public void setDoc(List<String> doc) {
		this.doc = doc;
	}
	public User getApprovedBy() {
		return approvedBy;
	}
	public void setApprovedBy(User approvedBy) {
		this.approvedBy = approvedBy;
	}
	public Date getApproveDate() {
		return approveDate;
	}
	public void setApproveDate(Date approveDate) {
		this.approveDate = approveDate;
	}
	
	
}
