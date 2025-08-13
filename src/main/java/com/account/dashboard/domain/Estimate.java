package com.account.dashboard.domain;

import java.util.Date;
import java.util.List;



import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;

public class Estimate {
 
//	@Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)	
//    Long id ;
//	private String productName;
//
//	
//	@NotBlank
//	@ManyToOne
//	Product product;
//	@ManyToOne
//	Contact primaryContact;
//	
//	@ManyToOne
//	Contact secondaryContact;
//	
//	
//	Date estimateData;
//	Date createDate;
//	
//	
//	/* --------------- company detail -------------------------------- */
//	Boolean isPresent;
//	String companyName;//isPresentFalse
//	Long companyId;  //isPrsentTrue
//
//	boolean isUnit;
//	String unitName; // isUnitFalse
//	Long unitId; //ISUNIT TRUE
//
//	String panNo;
//
//	String gstNo;
//	String gstType;
//	String gstDocuments;
//	String companyAge;
//
//	//primary
//	Boolean isPrimaryAddress=true;
//	String primaryTitle;
//	String Address;
//	String City;
//	String State;
//	String primaryPinCode;
//	String Country;
//
//	//secondary address
//	Boolean isSecondaryAddress=true;
//	String secondaryTitle;
//	String secondaryAddress;
//	String secondaryCity;
//	String secondaryState;
//	String secondaryPinCode;
//	String secondaryCountry;
//	
//	
//
//	@ManyToOne
//	User  assignee;
//	Long leadId;
////	Long productId;
//	String status;
//	
//	/* ---------------- product details  ---------------  */
//	
//	Boolean consultingSale;
//	String productType;
//	String orderNumber;
//	String purchaseDate;
//	List<String>cc;
//	String  invoiceNote;
//	String remarksForOption;
//	String documents;
//
//	
//    int  govermentfees;
//    String govermentCode;
//    String govermentGst;
//    int professionalFees;
//    String professionalCode;
//    String profesionalGst;
//    int serviceCharge;
//    String serviceCode;
//    String serviceGst;
//    int otherFees;
//    String otherCode;
//    String otherGst;	̥
//    boolean isDeleted;
//    
    
}
