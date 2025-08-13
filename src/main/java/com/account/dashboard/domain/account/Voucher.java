package com.account.dashboard.domain.account;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Table
@Entity
//@Data
public class Voucher {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	
	String companyName;
	
	Long estimateId;
	
	@ManyToOne
	Ledger ledger;
	
	@ManyToOne
	LedgerType ledgerType;
	
	@ManyToOne
	VoucherType voucherType;
	
	@ManyToOne
	Ledger product;
	
	boolean isCreditDebit;	
	String creditAmount ;
	String debitAmount;
	Date createDate;
	String paymentType;
 
	boolean isDeleted;
	
	// out side state
	boolean igstPresent;
	String igst;
	double igstCreditAmount;
	double igstDebitAmount;

	boolean cgstSgstPresent;
	String cgst;
	String sgst;
	double cgstCreditAmount;
	double cgstDebitAmount;
	double sgstCreditAmount;
	double sgstDebitAmount;
	double totalAmount;

	private double professionalGstAmount;

	public Long getId() {
		return id;
	}
	public String getCompanyName() {
		return companyName;
	}
	public Ledger getLedger() {
		return ledger;
	}
	public LedgerType getLedgerType() {
		return ledgerType;
	}
	public VoucherType getVoucherType() {
		return voucherType;
	}
	public Ledger getProduct() {
		return product;
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
	public boolean isDeleted() {
		return isDeleted;
	}
	public boolean isIgstPresent() {
		return igstPresent;
	}
	public String getIgst() {
		return igst;
	}
	public boolean isCgstSgstPresent() {
		return cgstSgstPresent;
	}
	public String getCgst() {
		return cgst;
	}
	public String getSgst() {
		return sgst;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	public void setLedger(Ledger ledger) {
		this.ledger = ledger;
	}
	public void setLedgerType(LedgerType ledgerType) {
		this.ledgerType = ledgerType;
	}
	public void setVoucherType(VoucherType voucherType) {
		this.voucherType = voucherType;
	}
	public void setProduct(Ledger product) {
		this.product = product;
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
	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}
	public void setIgstPresent(boolean igstPresent) {
		this.igstPresent = igstPresent;
	}
	public void setIgst(String igst) {
		this.igst = igst;
	}
	public void setCgstSgstPresent(boolean cgstSgstPresent) {
		this.cgstSgstPresent = cgstSgstPresent;
	}
	public void setCgst(String cgst) {
		this.cgst = cgst;
	}
	public void setSgst(String sgst) {
		this.sgst = sgst;
	}
	public Long getEstimateId() {
		return estimateId;
	}
	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}
	public double getProfessionalGstAmount() {
		return professionalGstAmount;
	}
	public void setProfessionalGstAmount(double professionalGstAmount) {
		this.professionalGstAmount = professionalGstAmount;
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
