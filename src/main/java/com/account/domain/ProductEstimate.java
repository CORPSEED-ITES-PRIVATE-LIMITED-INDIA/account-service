package com.account.domain;



import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Table
@Entity
public class ProductEstimate {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;	
	Long productSubCategoryId;
	String name;
	String type;
	
	private double serviceFees;
	private double serviceGstAmount;
	private int serviceGstPercent;
	
    double quantity;
    double totalPrice;
    double gstPercent;
    double gstAmount;
    double actualPrice;
	
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
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
	public Long getProductSubCategoryId() {
		return productSubCategoryId;
	}
	public void setProductSubCategoryId(Long productSubCategoryId) {
		this.productSubCategoryId = productSubCategoryId;
	}
	public double getGstPercent() {
		return gstPercent;
	}
	public void setGstPercent(double gstPercent) {
		this.gstPercent = gstPercent;
	}
	public double getGstAmount() {
		return gstAmount;
	}
	public void setGstAmount(double gstAmount) {
		this.gstAmount = gstAmount;
	}
	public double getActualPrice() {
		return actualPrice;
	}
	public void setActualPrice(double actualPrice) {
		this.actualPrice = actualPrice;
	}
    
	
    
}
