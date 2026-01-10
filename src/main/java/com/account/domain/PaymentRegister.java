package com.account.domain;

import java.util.Date;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 
 */
@Table
@Entity
@Getter
@Setter
//@Data
public class PaymentRegister {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	
	private Long leadId;
	private Long estimateId;
	
	private String billingQuantity;//partial ,full ,milestone
	private String paymentType;//Sales,
	
	
	String name;
	String emails;
	String contactNo;
	String whatsappNo;
	

	
	String registerBy;

	private Long createdById;
	private String transactionId;
	private String serviceName;

	private double  govermentfees;
	private double govermentGst;
	private int govermentGstPercent;

	private double professionalFees;
	private double profesionalGst;
	private int professionalGstPercent;
	private double professionalGstAmount;
	
	boolean tdsPresent;
	private double tdsAmount;
	private int tdsPercent;

	private double serviceCharge;
	private double serviceGst;
	private int serviceGstPercent;
	
    private double otherFees;
    private double otherGst;
    private int otherGstPercent;
    
	private double totalAmount;
	private String remark;
	private Date paymentDate;
	private String estimateNo;
	String status;

	int docPersent;
	int filingPersent;
	int liasoningPersent;
	int certificatePersent;

	String companyName;
	Long companyId;
	String updateDate;
	private Long approvedById;
	private Date approveDate;
	
	
	@ManyToMany(fetch = FetchType.EAGER)
	@JsonIgnore
	@JoinTable(name="payment_register_document",joinColumns = {@JoinColumn(name="payment_register_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="payment_register_document_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
	List<FileData>fileData;
	
	String registerType;// payment , purchase order
	String purchaseNumber;
	Date purchaseDate;
	String purchaseAttach;
	String paymentTerm;
	String comment;
	@ManyToOne
	User createdByUser;
	
	@Lob
	String termOfDelivery;
	String productType;
	
	private double amount;
	private double gstPercent;
    private double gstAmount;
    double quantity;
    double actualAmount;
    
	String modeOfPayment;
	Date referenceDate;
	String otherReference;
	String buyerOrderNo;
	double cgst;
	double sgst;
	double igst;


	
    
}
