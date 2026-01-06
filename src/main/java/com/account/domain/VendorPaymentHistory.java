package com.account.domain;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Table
@Entity
public class VendorPaymentHistory {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	private String serviceName;
	
	Long vendorPaymentRegisterId;

	private Long leadId;
	private Long estimateId;
	double actualAmount;
	
	double gst;
	double gstAmount;
    double tdsAmount;
    double tdsPercent;	
	double totalAmount;
	Date createDate;
	@ManyToOne
	User createBy;
    
	String document;
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
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
	public double getTdsPercent() {
		return tdsPercent;
	}
	public void setTdsPercent(double tdsPercent) {
		this.tdsPercent = tdsPercent;
	}
	public double getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}
	public double getActualAmount() {
		return actualAmount;
	}
	public void setActualAmount(double actualAmount) {
		this.actualAmount = actualAmount;
	}
	public User getCreateBy() {
		return createBy;
	}
	public void setCreateBy(User createBy) {
		this.createBy = createBy;
	}
	public Date getCreateDate() {
		return createDate;
	}
	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
	public Long getVendorPaymentRegisterId() {
		return vendorPaymentRegisterId;
	}
	public void setVendorPaymentRegisterId(Long vendorPaymentRegisterId) {
		this.vendorPaymentRegisterId = vendorPaymentRegisterId;
	}
	public String getDocument() {
		return document;
	}
	public void setDocument(String document) {
		this.document = document;
	}

	
	
	
	
}
