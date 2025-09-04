package com.account.dashboard.dto;

import java.util.Date;
import java.util.List;

public class UpdatePaymentDto {

	

	private Long id;

	private Long leadId;
	private Long estimateId;

	private String billingQuantity;
	private String paymentType;

	private String registerBy;

	private Long createdById;
	private String transactionId;
	private String serviceName;

	private double  govermentfees;
	private double govermentGst;
	private double govermentGstPercent;
	
	boolean tdsPresent;
	double tdsPercent;

	private double professionalFees;
	private double profesionalGst;
	private double profesionalGstPercent;

	private double serviceCharge;
	private double serviceGst;
	private double serviceGstPercent;

	private double otherFees;
	private double otherGst;
	private double otherGstPercent;

	private String UploadReceipt;
	private double totalAmount;
	private String remark;
	private Date paymentDate;
	private String estimateNo;
	private List<String>doc;

	String companyName;
	
	int docPersent;
	int filingPersent;
	int liasoningPersent;
	int certificatePersent;
	public Long getId() {
		return id;
	}
	public Long getLeadId() {
		return leadId;
	}
	public Long getEstimateId() {
		return estimateId;
	}
	public String getBillingQuantity() {
		return billingQuantity;
	}
	public String getPaymentType() {
		return paymentType;
	}
	public String getRegisterBy() {
		return registerBy;
	}
	public Long getCreatedById() {
		return createdById;
	}
	public String getTransactionId() {
		return transactionId;
	}
	public String getServiceName() {
		return serviceName;
	}
	public double getGovermentfees() {
		return govermentfees;
	}
	public double getGovermentGst() {
		return govermentGst;
	}
	public double getProfessionalFees() {
		return professionalFees;
	}
	public double getProfesionalGst() {
		return profesionalGst;
	}
	public double getServiceCharge() {
		return serviceCharge;
	}
	public double getServiceGst() {
		return serviceGst;
	}
	public double getOtherFees() {
		return otherFees;
	}
	public double getOtherGst() {
		return otherGst;
	}
	public String getUploadReceipt() {
		return UploadReceipt;
	}
	public double getTotalAmount() {
		return totalAmount;
	}
	public String getRemark() {
		return remark;
	}
	public Date getPaymentDate() {
		return paymentDate;
	}
	public String getEstimateNo() {
		return estimateNo;
	}
	public List<String> getDoc() {
		return doc;
	}
	public String getCompanyName() {
		return companyName;
	}
	public int getDocPersent() {
		return docPersent;
	}
	public int getFilingPersent() {
		return filingPersent;
	}
	public int getLiasoningPersent() {
		return liasoningPersent;
	}
	public int getCertificatePersent() {
		return certificatePersent;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public void setLeadId(Long leadId) {
		this.leadId = leadId;
	}
	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}
	public void setBillingQuantity(String billingQuantity) {
		this.billingQuantity = billingQuantity;
	}
	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}
	public void setRegisterBy(String registerBy) {
		this.registerBy = registerBy;
	}
	public void setCreatedById(Long createdById) {
		this.createdById = createdById;
	}
	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public void setGovermentfees(double govermentfees) {
		this.govermentfees = govermentfees;
	}
	public void setGovermentGst(double govermentGst) {
		this.govermentGst = govermentGst;
	}
	public void setProfessionalFees(double professionalFees) {
		this.professionalFees = professionalFees;
	}
	public void setProfesionalGst(double profesionalGst) {
		this.profesionalGst = profesionalGst;
	}
	public void setServiceCharge(double serviceCharge) {
		this.serviceCharge = serviceCharge;
	}
	public void setServiceGst(double serviceGst) {
		this.serviceGst = serviceGst;
	}
	public void setOtherFees(double otherFees) {
		this.otherFees = otherFees;
	}
	public void setOtherGst(double otherGst) {
		this.otherGst = otherGst;
	}
	public void setUploadReceipt(String uploadReceipt) {
		UploadReceipt = uploadReceipt;
	}
	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	public void setPaymentDate(Date paymentDate) {
		this.paymentDate = paymentDate;
	}
	public void setEstimateNo(String estimateNo) {
		this.estimateNo = estimateNo;
	}
	public void setDoc(List<String> doc) {
		this.doc = doc;
	}
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	public void setDocPersent(int docPersent) {
		this.docPersent = docPersent;
	}
	public void setFilingPersent(int filingPersent) {
		this.filingPersent = filingPersent;
	}
	public void setLiasoningPersent(int liasoningPersent) {
		this.liasoningPersent = liasoningPersent;
	}
	public void setCertificatePersent(int certificatePersent) {
		this.certificatePersent = certificatePersent;
	}
	public double getGovermentGstPercent() {
		return govermentGstPercent;
	}
	public double getProfesionalGstPercent() {
		return profesionalGstPercent;
	}
	public double getServiceGstPercent() {
		return serviceGstPercent;
	}
	public double getOtherGstPercent() {
		return otherGstPercent;
	}
	public void setGovermentGstPercent(double govermentGstPercent) {
		this.govermentGstPercent = govermentGstPercent;
	}
	public void setProfesionalGstPercent(double profesionalGstPercent) {
		this.profesionalGstPercent = profesionalGstPercent;
	}
	public void setServiceGstPercent(double serviceGstPercent) {
		this.serviceGstPercent = serviceGstPercent;
	}
	public void setOtherGstPercent(double otherGstPercent) {
		this.otherGstPercent = otherGstPercent;
	}
	public boolean isTdsPresent() {
		return tdsPresent;
	}
	public double getTdsPercent() {
		return tdsPercent;
	}
	public void setTdsPresent(boolean tdsPresent) {
		this.tdsPresent = tdsPresent;
	}
	public void setTdsPercent(double tdsPercent) {
		this.tdsPercent = tdsPercent;
	}
     
	
	
	

}
