package com.account.domain;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Table
@Entity
public class VendorPaymentHistory {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	private String serviceName;
	
	Long vendorPaymentRegisterId;

	private Long leadId;
	private Long estimateId;
	double actualAmount;
	
	double gst;
	double gstAmount;
    double tdsAmount;
    double tdsPercent;	
	double totalAmount;
	Date createDate;
	@ManyToOne
	User createBy;
    
	String document;
	

	
	
	
	
}
