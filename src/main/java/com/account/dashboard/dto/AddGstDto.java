package com.account.dashboard.dto;



public class AddGstDto {
	
	Long paymentRegisterId;
	double  gst;
	double gstAmount;
	String company;
	String type; //payable , reciveable
	
	String status;

	public Long getPaymentRegisterId() {
		return paymentRegisterId;
	}

	public void setPaymentRegisterId(Long paymentRegisterId) {
		this.paymentRegisterId = paymentRegisterId;
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

	public String getCompany() {
		return company;
	}

	public void setCompany(String company) {
		this.company = company;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	

	
	
}
