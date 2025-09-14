package com.account.dashboard.domain.account;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Table
@Entity
//@Data
public class Voucher implements Serializable {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	
	String companyName;
	
	Long estimateId;
	
	@ManyToOne(fetch = FetchType.LAZY)
	Ledger ledger;
	
	String impact;  // direct or indirect
	
	@ManyToOne
	LedgerType ledgerType;
	
	@ManyToOne
	VoucherType voucherType;
	
	@ManyToOne
	Ledger product;
	
	@ManyToOne
	Voucher igstCreditVoucher;
	
	@ManyToOne
	Voucher igstDebitVoucher;
	
	boolean isCreditDebit;	
	double creditAmount ;
	double debitAmount;
	Date createDate;
	String paymentType;
 
	boolean isDeleted;
	
	// out side state
	boolean igstPresent;
	String igst;

	boolean cgstSgstPresent;
	@ManyToOne
	Voucher cgstCreditVoucher;	
	@ManyToOne
	Voucher sgstCreditVoucher;
	
	@ManyToOne
	Voucher cgstDebitVoucher;	
	@ManyToOne
	Voucher sgstDebitVoucher;
	
	
//	
	String cgst;
	String sgst;
//	double cgstCreditAmount;
//	double cgstDebitAmount;
//	double sgstCreditAmount;
//	double sgstDebitAmount;
	double totalAmount;

	private double professionalGstAmount;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public Long getEstimateId() {
		return estimateId;
	}

	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}

	public Ledger getLedger() {
		return ledger;
	}

	public void setLedger(Ledger ledger) {
		this.ledger = ledger;
	}

	public LedgerType getLedgerType() {
		return ledgerType;
	}

	public void setLedgerType(LedgerType ledgerType) {
		this.ledgerType = ledgerType;
	}

	public VoucherType getVoucherType() {
		return voucherType;
	}

	public void setVoucherType(VoucherType voucherType) {
		this.voucherType = voucherType;
	}

	public Ledger getProduct() {
		return product;
	}

	public void setProduct(Ledger product) {
		this.product = product;
	}

	public Voucher getIgstCreditVoucher() {
		return igstCreditVoucher;
	}

	public void setIgstCreditVoucher(Voucher igstCreditVoucher) {
		this.igstCreditVoucher = igstCreditVoucher;
	}

	public Voucher getIgstDebitVoucher() {
		return igstDebitVoucher;
	}

	public void setIgstDebitVoucher(Voucher igstDebitVoucher) {
		this.igstDebitVoucher = igstDebitVoucher;
	}

	public boolean isCreditDebit() {
		return isCreditDebit;
	}

	public void setCreditDebit(boolean isCreditDebit) {
		this.isCreditDebit = isCreditDebit;
	}

    

	public double getCreditAmount() {
		return creditAmount;
	}

	public void setCreditAmount(double creditAmount) {
		this.creditAmount = creditAmount;
	}

	public double getDebitAmount() {
		return debitAmount;
	}

	public void setDebitAmount(double debitAmount) {
		this.debitAmount = debitAmount;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}

	public boolean isIgstPresent() {
		return igstPresent;
	}

	public void setIgstPresent(boolean igstPresent) {
		this.igstPresent = igstPresent;
	}

	public boolean isCgstSgstPresent() {
		return cgstSgstPresent;
	}

	public void setCgstSgstPresent(boolean cgstSgstPresent) {
		this.cgstSgstPresent = cgstSgstPresent;
	}

	public Voucher getCgstCreditVoucher() {
		return cgstCreditVoucher;
	}

	public void setCgstCreditVoucher(Voucher cgstCreditVoucher) {
		this.cgstCreditVoucher = cgstCreditVoucher;
	}

	public Voucher getSgstCreditVoucher() {
		return sgstCreditVoucher;
	}

	public void setSgstCreditVoucher(Voucher sgstCreditVoucher) {
		this.sgstCreditVoucher = sgstCreditVoucher;
	}

	public Voucher getCgstDebitVoucher() {
		return cgstDebitVoucher;
	}

	public void setCgstDebitVoucher(Voucher cgstDebitVoucher) {
		this.cgstDebitVoucher = cgstDebitVoucher;
	}

	public Voucher getSgstDebitVoucher() {
		return sgstDebitVoucher;
	}

	public void setSgstDebitVoucher(Voucher sgstDebitVoucher) {
		this.sgstDebitVoucher = sgstDebitVoucher;
	}

	public double getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}

	public double getProfessionalGstAmount() {
		return professionalGstAmount;
	}

	public void setProfessionalGstAmount(double professionalGstAmount) {
		this.professionalGstAmount = professionalGstAmount;
	}

	public String getIgst() {
		return igst;
	}

	public void setIgst(String igst) {
		this.igst = igst;
	}

	public String getCgst() {
		return cgst;
	}

	public void setCgst(String cgst) {
		this.cgst = cgst;
	}

	public String getSgst() {
		return sgst;
	}

	public void setSgst(String sgst) {
		this.sgst = sgst;
	}

	public String getImpact() {
		return impact;
	}

	public void setImpact(String impact) {
		this.impact = impact;
	}

	
	
}
