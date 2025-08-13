package com.account.dashboard.domain;

import com.account.dashboard.domain.account.Voucher;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Table
@Entity
public class GstDetails {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;

    double gstAmount;
    double cgstPercent;
    double sgstPercent;
    double igstPercent;
    double totalGst;
    String gstType;
    @ManyToOne
    Voucher voucher;
    
	public Long getId() {
		return id;
	}
	public double getGstAmount() {
		return gstAmount;
	}
	public double getCgstPercent() {
		return cgstPercent;
	}
	public double getSgstPercent() {
		return sgstPercent;
	}
	public double getIgstPercent() {
		return igstPercent;
	}
	public double getTotalGst() {
		return totalGst;
	}
	public String getGstType() {
		return gstType;
	}
	public Voucher getVoucher() {
		return voucher;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public void setGstAmount(double gstAmount) {
		this.gstAmount = gstAmount;
	}
	public void setCgstPercent(double cgstPercent) {
		this.cgstPercent = cgstPercent;
	}
	public void setSgstPercent(double sgstPercent) {
		this.sgstPercent = sgstPercent;
	}
	public void setIgstPercent(double igstPercent) {
		this.igstPercent = igstPercent;
	}
	public void setTotalGst(double totalGst) {
		this.totalGst = totalGst;
	}
	public void setGstType(String gstType) {
		this.gstType = gstType;
	}
	public void setVoucher(Voucher voucher) {
		this.voucher = voucher;
	}
    
    
    
}
