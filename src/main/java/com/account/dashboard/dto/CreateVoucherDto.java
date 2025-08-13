package com.account.dashboard.dto;

import java.util.Date;


import com.account.dashboard.domain.account.Ledger;
import com.account.dashboard.domain.account.LedgerType;
import com.account.dashboard.domain.account.VoucherType;

import jakarta.persistence.ManyToOne;
import lombok.*;


//@Getter
//@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateVoucherDto {

		
	String companyName;
	
	long ledgerId;
	
	long ledgerTypeId;
	
	long voucherTypeId;
	
	long productId;
	
	boolean isCreditDebit;
	
	String creditAmount ;
	String debitAmount;
	Date createDate;
	
	String paymentType;
	
	
	boolean igstPresent;
	String igst;
	double igstCreditAmount;
	double igstDebitAmount;
	
	boolean cgstIgstPresent;
	String cgst;
	String sgst;
	double cgstCreditAmount;
	double cgstDebitAmount;

	double sgstCreditAmount;
	double sgstDebitAmount;
	double totalAmount;

	public String getCompanyName() {
		return companyName;
	}
	public long getLedgerId() {
		return ledgerId;
	}
	public long getLedgerTypeId() {
		return ledgerTypeId;
	}
	public long getVoucherTypeId() {
		return voucherTypeId;
	}
	public long getProductId() {
		return productId;
	}
	public boolean isCreditDebit() {
		return isCreditDebit;
	}
	public String getCreditAmount() {
		return creditAmount;
	}
	public String getDebitAmount() {
		return debitAmount;
	}
	public Date getCreateDate() {
		return createDate;
	}
	public String getPaymentType() {
		return paymentType;
	}
	public boolean isIgstPresent() {
		return igstPresent;
	}
	public String getIgst() {
		return igst;
	}
	public boolean isCgstIgstPresent() {
		return cgstIgstPresent;
	}
	public String getCgst() {
		return cgst;
	}
	public String getSgst() {
		return sgst;
	}
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	public void setLedgerId(long ledgerId) {
		this.ledgerId = ledgerId;
	}
	public void setLedgerTypeId(long ledgerTypeId) {
		this.ledgerTypeId = ledgerTypeId;
	}
	public void setVoucherTypeId(long voucherTypeId) {
		this.voucherTypeId = voucherTypeId;
	}
	public void setProductId(long productId) {
		this.productId = productId;
	}
	public void setCreditDebit(boolean isCreditDebit) {
		this.isCreditDebit = isCreditDebit;
	}
	public void setCreditAmount(String creditAmount) {
		this.creditAmount = creditAmount;
	}
	public void setDebitAmount(String debitAmount) {
		this.debitAmount = debitAmount;
	}
	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}
	public void setIgstPresent(boolean igstPresent) {
		this.igstPresent = igstPresent;
	}
	public void setIgst(String igst) {
		this.igst = igst;
	}
	public void setCgstIgstPresent(boolean cgstIgstPresent) {
		this.cgstIgstPresent = cgstIgstPresent;
	}
	public void setCgst(String cgst) {
		this.cgst = cgst;
	}
	public void setSgst(String sgst) {
		this.sgst = sgst;
	}
	public double getIgstCreditAmount() {
		return igstCreditAmount;
	}
	public double getIgstDebitAmount() {
		return igstDebitAmount;
	}
	public double getCgstCreditAmount() {
		return cgstCreditAmount;
	}
	public double getCgstDebitAmount() {
		return cgstDebitAmount;
	}
	public double getSgstCreditAmount() {
		return sgstCreditAmount;
	}
	public double getSgstDebitAmount() {
		return sgstDebitAmount;
	}
	public void setIgstCreditAmount(double igstCreditAmount) {
		this.igstCreditAmount = igstCreditAmount;
	}
	public void setIgstDebitAmount(double igstDebitAmount) {
		this.igstDebitAmount = igstDebitAmount;
	}
	public void setCgstCreditAmount(double cgstCreditAmount) {
		this.cgstCreditAmount = cgstCreditAmount;
	}
	public void setCgstDebitAmount(double cgstDebitAmount) {
		this.cgstDebitAmount = cgstDebitAmount;
	}
	public void setSgstCreditAmount(double sgstCreditAmount) {
		this.sgstCreditAmount = sgstCreditAmount;
	}
	public void setSgstDebitAmount(double sgstDebitAmount) {
		this.sgstDebitAmount = sgstDebitAmount;
	}
	public double getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}
	
	
	
}
