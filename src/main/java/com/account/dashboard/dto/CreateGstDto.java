package com.account.dashboard.dto;

import com.account.dashboard.domain.account.Voucher;

public class CreateGstDto {

    double gstAmount;
    double cgstPercent;
    double sgstPercent;
    double igstPercent;
    double totalGst;
    String gstType;
    Long voucherId;
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
	public Long getVoucherId() {
		return voucherId;
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
	public void setVoucherId(Long voucherId) {
		this.voucherId = voucherId;
	}
    
    

}
