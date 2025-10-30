package com.account.dashboard.domain;

import java.util.Date;
import java.util.List;


import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Table
@Entity
@Data
public class BankStatement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne
	BankAccount bankAccount;
	
	String transactionId;
	
	double totalAmount;
	
	double leftAmount;
	
	String name;
	
	Date createDate;
	
	Date paymentDate;
	
	Date updateDate;
	
	// over all  history
//	
	@ManyToMany(fetch = FetchType.EAGER,cascade = CascadeType.ALL)
	@JoinTable(name="bank_statement_payment_register",joinColumns = {@JoinColumn(name="payment_register_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="bank_statement_payment_register_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
	List<PaymentRegister>paymentRegister;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public double getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}

	public double getLeftAmount() {
		return leftAmount;
	}

	public void setLeftAmount(double leftAmount) {
		this.leftAmount = leftAmount;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public Date getPaymentDate() {
		return paymentDate;
	}

	public void setPaymentDate(Date paymentDate) {
		this.paymentDate = paymentDate;
	}

	public Date getUpdateDate() {
		return updateDate;
	}

	public void setUpdateDate(Date updateDate) {
		this.updateDate = updateDate;
	}

	public List<PaymentRegister> getPaymentRegister() {
		return paymentRegister;
	}

	public void setPaymentRegister(List<PaymentRegister> paymentRegister) {
		this.paymentRegister = paymentRegister;
	}
	
	
	
	
	
	
}
