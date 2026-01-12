package com.account.domain;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

//@Data
@Table
@Entity
public class InvoiceData {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)	
    Long id ;
	private String productName;
	
    Long estimateId;
    
    String invoiceNo;

//   primary contact	̥
    Long   primaryContactId;
	String primaryContactTitle;
	String primaryContactName;
	String primaryContactemails;
	String primaryContactNo;
	String primaryWhatsappNo;
	String primaryContactDesignation;
	
//	 secondary contact 
    Long   secondaryContactId;
	String secondaryContactTitle;
	String secondaryContactName;
	String secondaryContactemails;
	String secondaryContactNo;
	String secondaryWhatsappNo;
	String secondaryContactDesignation;
	
	Date estimateDate;
	Date createDate;
	String estimateNo;
	
	/* --------------- company detail -------------------------------- */
	Boolean isPresent;
	String companyName;//isPresentFalse
	Long companyId;  //isPrsentTrue

	boolean isUnit;
	String unitName; // isUnitFalse
	Long unitId; //ISUNIT TRUE

	String panNo;

	String gstNo;
	String gstType;
	String gstDocuments;
	String companyAge;

	//primary
	Boolean isPrimaryAddress=true;
	String primaryTitle;
	String Address;
	String City;
	String State;
	String primaryPinCode;
	String Country;

	//secondary address
	Boolean isSecondaryAddress=true;
	String secondaryTitle;
	String secondaryAddress;
	String secondaryCity;
	String secondaryState;
	String secondaryPinCode;
	String secondaryCountry;
	
	//hsn details
    boolean hsnSacPrsent;
	String hsnSacDetails;
	String HsnSac;
	String hsnDescription;

	@ManyToOne
	User  assignee;
	Long leadId;
//	Long productId;
	String status;
	
	/* ---------------- product details  ---------------  */
	
	Boolean consultingSale;
	String productType;
	String orderNumber;
	String purchaseDate;
	List<String>cc;
	String  invoiceNote;
	String remarksForOption;
	String documents;

	
    String  govermentfees;
    String govermentCode;
    String govermentGst;
    String professionalFees;
    String professionalCode;
    String profesionalGst;
    String serviceCharge;
    String serviceCode;
    String serviceGst;
    String otherFees;
    String otherCode;
    String otherGst;
    String totalAmount;
    String paidAmount;
    
	private double amount;
	private double gstPercent;
    private double gstAmount;
    
    @ManyToOne
    @JoinColumn(name = "unbilled_id")
    private Unbilled unbilled;
    
    @Lob
	String termOfDelivery;
    
	String modeOfPayment;
	Date referenceDate;
	String otherReference;
	String buyerOrderNo;
	boolean cgstSgstPresent;
	double cgst;
	double sgst;
	boolean igstPresent;
	double igst;
	
    double quantity;
    double actualAmount;

    boolean isDeleted;
    

	
    
}
