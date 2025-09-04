package com.account.dashboard.domain;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
@Data
@Table
@Entity
public class InvoiceData {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)	
    Long id ;
	private String productName;
	
    Long estimateId;
    
    

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
	
	Date estimateData;
	Date createDate;
	
	
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
    
    @ManyToOne
    @JoinColumn(name = "unbilled_id")
    private Unbilled unbilled;
    
    boolean isDeleted;
    
	public Long getId() {
		return id;
	}
	public String getProductName() {
		return productName;
	}
	public Long getEstimateId() {
		return estimateId;
	}
	public Long getPrimaryContactId() {
		return primaryContactId;
	}
	public String getPrimaryContactTitle() {
		return primaryContactTitle;
	}
	public String getPrimaryContactName() {
		return primaryContactName;
	}
	public String getPrimaryContactemails() {
		return primaryContactemails;
	}
	public String getPrimaryContactNo() {
		return primaryContactNo;
	}
	public String getPrimaryWhatsappNo() {
		return primaryWhatsappNo;
	}
	public String getPrimaryContactDesignation() {
		return primaryContactDesignation;
	}
	public Long getSecondaryContactId() {
		return secondaryContactId;
	}
	public String getSecondaryContactTitle() {
		return secondaryContactTitle;
	}
	public String getSecondaryContactName() {
		return secondaryContactName;
	}
	public String getSecondaryContactemails() {
		return secondaryContactemails;
	}
	public String getSecondaryContactNo() {
		return secondaryContactNo;
	}
	public String getSecondaryWhatsappNo() {
		return secondaryWhatsappNo;
	}
	public String getSecondaryContactDesignation() {
		return secondaryContactDesignation;
	}
	public Date getEstimateData() {
		return estimateData;
	}
	public Date getCreateDate() {
		return createDate;
	}
	public Boolean getIsPresent() {
		return isPresent;
	}
	public String getCompanyName() {
		return companyName;
	}
	public Long getCompanyId() {
		return companyId;
	}
	public boolean isUnit() {
		return isUnit;
	}
	public String getUnitName() {
		return unitName;
	}
	public Long getUnitId() {
		return unitId;
	}
	public String getPanNo() {
		return panNo;
	}
	public String getGstNo() {
		return gstNo;
	}
	public String getGstType() {
		return gstType;
	}
	public String getGstDocuments() {
		return gstDocuments;
	}
	public String getCompanyAge() {
		return companyAge;
	}
	public Boolean getIsPrimaryAddress() {
		return isPrimaryAddress;
	}
	public String getPrimaryTitle() {
		return primaryTitle;
	}
	public String getAddress() {
		return Address;
	}
	public String getCity() {
		return City;
	}
	public String getState() {
		return State;
	}
	public String getPrimaryPinCode() {
		return primaryPinCode;
	}
	public String getCountry() {
		return Country;
	}
	public Boolean getIsSecondaryAddress() {
		return isSecondaryAddress;
	}
	public String getSecondaryTitle() {
		return secondaryTitle;
	}
	public String getSecondaryAddress() {
		return secondaryAddress;
	}
	public String getSecondaryCity() {
		return secondaryCity;
	}
	public String getSecondaryState() {
		return secondaryState;
	}
	public String getSecondaryPinCode() {
		return secondaryPinCode;
	}
	public String getSecondaryCountry() {
		return secondaryCountry;
	}
	public boolean isHsnSacPrsent() {
		return hsnSacPrsent;
	}
	public String getHsnSacDetails() {
		return hsnSacDetails;
	}
	public String getHsnSac() {
		return HsnSac;
	}
	public String getHsnDescription() {
		return hsnDescription;
	}
	public User getAssignee() {
		return assignee;
	}
	public Long getLeadId() {
		return leadId;
	}
	public String getStatus() {
		return status;
	}
	public Boolean getConsultingSale() {
		return consultingSale;
	}
	public String getProductType() {
		return productType;
	}
	public String getOrderNumber() {
		return orderNumber;
	}
	public String getPurchaseDate() {
		return purchaseDate;
	}
	public List<String> getCc() {
		return cc;
	}
	public String getInvoiceNote() {
		return invoiceNote;
	}
	public String getRemarksForOption() {
		return remarksForOption;
	}
	public String getDocuments() {
		return documents;
	}
	public String getGovermentfees() {
		return govermentfees;
	}
	public String getGovermentCode() {
		return govermentCode;
	}
	public String getGovermentGst() {
		return govermentGst;
	}
	public String getProfessionalFees() {
		return professionalFees;
	}
	public String getProfessionalCode() {
		return professionalCode;
	}
	public String getProfesionalGst() {
		return profesionalGst;
	}
	public String getServiceCharge() {
		return serviceCharge;
	}
	public String getServiceCode() {
		return serviceCode;
	}
	public String getServiceGst() {
		return serviceGst;
	}
	public String getOtherFees() {
		return otherFees;
	}
	public String getOtherCode() {
		return otherCode;
	}
	public String getOtherGst() {
		return otherGst;
	}
	public boolean isDeleted() {
		return isDeleted;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public void setProductName(String productName) {
		this.productName = productName;
	}
	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}
	public void setPrimaryContactId(Long primaryContactId) {
		this.primaryContactId = primaryContactId;
	}
	public void setPrimaryContactTitle(String primaryContactTitle) {
		this.primaryContactTitle = primaryContactTitle;
	}
	public void setPrimaryContactName(String primaryContactName) {
		this.primaryContactName = primaryContactName;
	}
	public void setPrimaryContactemails(String primaryContactemails) {
		this.primaryContactemails = primaryContactemails;
	}
	public void setPrimaryContactNo(String primaryContactNo) {
		this.primaryContactNo = primaryContactNo;
	}
	public void setPrimaryWhatsappNo(String primaryWhatsappNo) {
		this.primaryWhatsappNo = primaryWhatsappNo;
	}
	public void setPrimaryContactDesignation(String primaryContactDesignation) {
		this.primaryContactDesignation = primaryContactDesignation;
	}
	public void setSecondaryContactId(Long secondaryContactId) {
		this.secondaryContactId = secondaryContactId;
	}
	public void setSecondaryContactTitle(String secondaryContactTitle) {
		this.secondaryContactTitle = secondaryContactTitle;
	}
	public void setSecondaryContactName(String secondaryContactName) {
		this.secondaryContactName = secondaryContactName;
	}
	public void setSecondaryContactemails(String secondaryContactemails) {
		this.secondaryContactemails = secondaryContactemails;
	}
	public void setSecondaryContactNo(String secondaryContactNo) {
		this.secondaryContactNo = secondaryContactNo;
	}
	public void setSecondaryWhatsappNo(String secondaryWhatsappNo) {
		this.secondaryWhatsappNo = secondaryWhatsappNo;
	}
	public void setSecondaryContactDesignation(String secondaryContactDesignation) {
		this.secondaryContactDesignation = secondaryContactDesignation;
	}
	public void setEstimateData(Date estimateData) {
		this.estimateData = estimateData;
	}
	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}
	public void setIsPresent(Boolean isPresent) {
		this.isPresent = isPresent;
	}
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	public void setCompanyId(Long companyId) {
		this.companyId = companyId;
	}
	public void setUnit(boolean isUnit) {
		this.isUnit = isUnit;
	}
	public void setUnitName(String unitName) {
		this.unitName = unitName;
	}
	public void setUnitId(Long unitId) {
		this.unitId = unitId;
	}
	public void setPanNo(String panNo) {
		this.panNo = panNo;
	}
	public void setGstNo(String gstNo) {
		this.gstNo = gstNo;
	}
	public void setGstType(String gstType) {
		this.gstType = gstType;
	}
	public void setGstDocuments(String gstDocuments) {
		this.gstDocuments = gstDocuments;
	}
	public void setCompanyAge(String companyAge) {
		this.companyAge = companyAge;
	}
	public void setIsPrimaryAddress(Boolean isPrimaryAddress) {
		this.isPrimaryAddress = isPrimaryAddress;
	}
	public void setPrimaryTitle(String primaryTitle) {
		this.primaryTitle = primaryTitle;
	}
	public void setAddress(String address) {
		Address = address;
	}
	public void setCity(String city) {
		City = city;
	}
	public void setState(String state) {
		State = state;
	}
	public void setPrimaryPinCode(String primaryPinCode) {
		this.primaryPinCode = primaryPinCode;
	}
	public void setCountry(String country) {
		Country = country;
	}
	public void setIsSecondaryAddress(Boolean isSecondaryAddress) {
		this.isSecondaryAddress = isSecondaryAddress;
	}
	public void setSecondaryTitle(String secondaryTitle) {
		this.secondaryTitle = secondaryTitle;
	}
	public void setSecondaryAddress(String secondaryAddress) {
		this.secondaryAddress = secondaryAddress;
	}
	public void setSecondaryCity(String secondaryCity) {
		this.secondaryCity = secondaryCity;
	}
	public void setSecondaryState(String secondaryState) {
		this.secondaryState = secondaryState;
	}
	public void setSecondaryPinCode(String secondaryPinCode) {
		this.secondaryPinCode = secondaryPinCode;
	}
	public void setSecondaryCountry(String secondaryCountry) {
		this.secondaryCountry = secondaryCountry;
	}
	public void setHsnSacPrsent(boolean hsnSacPrsent) {
		this.hsnSacPrsent = hsnSacPrsent;
	}
	public void setHsnSacDetails(String hsnSacDetails) {
		this.hsnSacDetails = hsnSacDetails;
	}
	public void setHsnSac(String hsnSac) {
		HsnSac = hsnSac;
	}
	public void setHsnDescription(String hsnDescription) {
		this.hsnDescription = hsnDescription;
	}
	public void setAssignee(User assignee) {
		this.assignee = assignee;
	}
	public void setLeadId(Long leadId) {
		this.leadId = leadId;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public void setConsultingSale(Boolean consultingSale) {
		this.consultingSale = consultingSale;
	}
	public void setProductType(String productType) {
		this.productType = productType;
	}
	public void setOrderNumber(String orderNumber) {
		this.orderNumber = orderNumber;
	}
	public void setPurchaseDate(String purchaseDate) {
		this.purchaseDate = purchaseDate;
	}
	public void setCc(List<String> cc) {
		this.cc = cc;
	}
	public void setInvoiceNote(String invoiceNote) {
		this.invoiceNote = invoiceNote;
	}
	public void setRemarksForOption(String remarksForOption) {
		this.remarksForOption = remarksForOption;
	}
	public void setDocuments(String documents) {
		this.documents = documents;
	}
	public void setGovermentfees(String govermentfees) {
		this.govermentfees = govermentfees;
	}
	public void setGovermentCode(String govermentCode) {
		this.govermentCode = govermentCode;
	}
	public void setGovermentGst(String govermentGst) {
		this.govermentGst = govermentGst;
	}
	public void setProfessionalFees(String professionalFees) {
		this.professionalFees = professionalFees;
	}
	public void setProfessionalCode(String professionalCode) {
		this.professionalCode = professionalCode;
	}
	public void setProfesionalGst(String profesionalGst) {
		this.profesionalGst = profesionalGst;
	}
	public void setServiceCharge(String serviceCharge) {
		this.serviceCharge = serviceCharge;
	}
	public void setServiceCode(String serviceCode) {
		this.serviceCode = serviceCode;
	}
	public void setServiceGst(String serviceGst) {
		this.serviceGst = serviceGst;
	}
	public void setOtherFees(String otherFees) {
		this.otherFees = otherFees;
	}
	public void setOtherCode(String otherCode) {
		this.otherCode = otherCode;
	}
	public void setOtherGst(String otherGst) {
		this.otherGst = otherGst;
	}
	public void setDeleted(boolean isDeleted) {
		this.isDeleted = isDeleted;
	}
	public String getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(String totalAmount) {
		this.totalAmount = totalAmount;
	}
    
	
    
}
