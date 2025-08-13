package com.account.dashboard.dto;

import java.util.Date;

public class CreateBankStatementDto {
   
    String transactionId;
	
	double totalAmount;
	
	double leftAmount;
	
	Date paymentDate;
	
	String name;

	public String getTransactionId() {
		return transactionId;
	}

	public double getTotalAmount() {
		return totalAmount;
	}

	public double getLeftAmount() {
		return leftAmount;
	}

	public String getName() {
		return name;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}

	public void setLeftAmount(double leftAmount) {
		this.leftAmount = leftAmount;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Date getPaymentDate() {
		return paymentDate;
	}

	public void setPaymentDate(Date paymentDate) {
		this.paymentDate = paymentDate;
	}
	
	
}
