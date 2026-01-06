package com.account.dto;

public class ProductEstimateDto {
	
	String name;
	String type;
	
	private double serviceFees;
	private double serviceGstAmount;
	private int serviceGstPercent;
	
    double quantity;
    double totalPrice;
    
	boolean tdsPresent;
	private double tdsAmount;
	private int tdsPercent;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public double getServiceFees() {
		return serviceFees;
	}
	public void setServiceFees(double serviceFees) {
		this.serviceFees = serviceFees;
	}
	public double getServiceGstAmount() {
		return serviceGstAmount;
	}
	public void setServiceGstAmount(double serviceGstAmount) {
		this.serviceGstAmount = serviceGstAmount;
	}
	public int getServiceGstPercent() {
		return serviceGstPercent;
	}
	public void setServiceGstPercent(int serviceGstPercent) {
		this.serviceGstPercent = serviceGstPercent;
	}
	public double getQuantity() {
		return quantity;
	}
	public void setQuantity(double quantity) {
		this.quantity = quantity;
	}
	public double getTotalPrice() {
		return totalPrice;
	}
	public void setTotalPrice(double totalPrice) {
		this.totalPrice = totalPrice;
	}
	public boolean isTdsPresent() {
		return tdsPresent;
	}
	public void setTdsPresent(boolean tdsPresent) {
		this.tdsPresent = tdsPresent;
	}
	public double getTdsAmount() {
		return tdsAmount;
	}
	public void setTdsAmount(double tdsAmount) {
		this.tdsAmount = tdsAmount;
	}
	public int getTdsPercent() {
		return tdsPercent;
	}
	public void setTdsPercent(int tdsPercent) {
		this.tdsPercent = tdsPercent;
	}
	
	
	
}
