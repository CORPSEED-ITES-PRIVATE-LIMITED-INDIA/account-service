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
import jakarta.persistence.Table;
import lombok.Data;

@Table
@Entity
@Data
public class BankStatement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
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
	
	
	
	
	
	
}
