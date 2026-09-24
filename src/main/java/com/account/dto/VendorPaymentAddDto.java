package com.account.dto;

public class VendorPaymentAddDto {

	Long vendorPaymentId;
	 String serviceName;

	 Long leadId;
	 Long estimateId;
	
	double actualAmount;
	
	double gst;
	double gstAmount;
	
    double tdsAmount;
    int tdsPercent;	
    
	double totalAmount;

	Long createBy;
	String status;
	String document;
	
	
	public String getServiceName() {
		return serviceName;
	}
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}
	public Long getLeadId() {
		return leadId;
	}
	public void setLeadId(Long leadId) {
		this.leadId = leadId;
	}
	public Long getEstimateId() {
		return estimateId;
	}
	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}
	public double getActualAmount() {
		return actualAmount;
	}
	public void setActualAmount(double actualAmount) {
		this.actualAmount = actualAmount;
	}
	public double getGst() {
		return gst;
	}
	public void setGst(double gst) {
		this.gst = gst;
	}
	public double getGstAmount() {
		return gstAmount;
	}
	public void setGstAmount(double gstAmount) {
		this.gstAmount = gstAmount;
	}
	public double getTdsAmount() {
		return tdsAmount;
	}
	public void setTdsAmount(double tdsAmount) {
		this.tdsAmount = tdsAmount;
	}

	public double getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}
	public Long getCreateBy() {
		return createBy;
	}
	public void setCreateBy(Long createBy) {
		this.createBy = createBy;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getDocument() {
		return document;
	}
	public void setDocument(String document) {
		this.document = document;
	}
	public Long getVendorPaymentId() {
		return vendorPaymentId;
	}
	public void setVendorPaymentId(Long vendorPaymentId) {
		this.vendorPaymentId = vendorPaymentId;
	}
	public int getTdsPercent() {
		return tdsPercent;
	}
	public void setTdsPercent(int tdsPercent) {
		this.tdsPercent = tdsPercent;
	}
	
	
	
	
	
}
