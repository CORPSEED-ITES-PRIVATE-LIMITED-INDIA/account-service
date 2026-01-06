package com.account.dto;

import java.util.Date;
import java.util.List;

import jakarta.persistence.Lob;

public class CreateAmountDto {
	

	String name;
	String emails;
	String contactNo;
	String whatsappNo;
    String providerType;
	private Long leadId;
	private Long estimateId;

	private String billingQuantity;
	private String paymentType;// full , prtial and milestone
	private Long companyId;
    
	private String registerBy;

	private Long createdById;
	private String transactionId;
	private String serviceName;

	private double  govermentfees;
	private double govermentGst;
	private int govermentGstPercent;
	
	private double professionalFees;
	private double profesionalGst;
	private double profesionalGstFee;

	private double serviceCharge;
	private double serviceGst;
	private int serviceGstPercent;

	private double otherFees;
	private double otherGst;
	private int otherGstPercent;

	int docPersent;
	int filingPersent;
	int liasoningPersent;
	int certificatePersent;


	private String UploadReceipt;
	private double totalAmount;
	private double amount;
	private double gstPercent;
    private double gstAmount;
	private String remark;
	private Date paymentDate;
	private String estimateNo;
	private List<String>doc;

	String companyName;
	
	boolean tdsPresent;
	int tdsPercent;
	
	
	String termOfDelivery;
	String productType;
	
	//
	String modeOfPayment;
	Date referenceDate;
	String otherReference;
	String buyerOrderNo;
	double cgst;
	double sgst;
	double igst;
    double quantity;
    public double getActualPrice() {
		return actualPrice;
	}

	public void setActualPrice(double actualPrice) {
		this.actualPrice = actualPrice;
	}

	double actualPrice;
	

	public Long getLeadId() {
		return leadId;
	}

	public Long getEstimateId() {
		return estimateId;
	}

	public String getBillingQuantity() {
		return billingQuantity;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public String getRegisterBy() {
		return registerBy;
	}

	public Long getCreatedById() {
		return createdById;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public String getServiceName() {
		return serviceName;
	}

	public double getGovermentfees() {
		return govermentfees;
	}

	public double getGovermentGst() {
		return govermentGst;
	}

	public int getGovermentGstPercent() {
		return govermentGstPercent;
	}

	public double getProfessionalFees() {
		return professionalFees;
	}

	public double getProfesionalGst() {
		return profesionalGst;
	}


	public double getServiceCharge() {
		return serviceCharge;
	}

	public double getServiceGst() {
		return serviceGst;
	}

	public int getServiceGstPercent() {
		return serviceGstPercent;
	}

	public double getOtherFees() {
		return otherFees;
	}

	public double getOtherGst() {
		return otherGst;
	}

	public int getOtherGstPercent() {
		return otherGstPercent;
	}

	public int getDocPersent() {
		return docPersent;
	}

	public int getFilingPersent() {
		return filingPersent;
	}

	public int getLiasoningPersent() {
		return liasoningPersent;
	}

	public int getCertificatePersent() {
		return certificatePersent;
	}

	public String getUploadReceipt() {
		return UploadReceipt;
	}

	public double getTotalAmount() {
		return totalAmount;
	}

	public String getRemark() {
		return remark;
	}

	public Date getPaymentDate() {
		return paymentDate;
	}

	public String getEstimateNo() {
		return estimateNo;
	}

	public List<String> getDoc() {
		return doc;
	}

	public String getCompanyName() {
		return companyName;
	}

	public void setLeadId(Long leadId) {
		this.leadId = leadId;
	}

	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}

	public void setBillingQuantity(String billingQuantity) {
		this.billingQuantity = billingQuantity;
	}

	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
	}

	public void setRegisterBy(String registerBy) {
		this.registerBy = registerBy;
	}

	public void setCreatedById(Long createdById) {
		this.createdById = createdById;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public void setGovermentfees(double govermentfees) {
		this.govermentfees = govermentfees;
	}

	public void setGovermentGst(double govermentGst) {
		this.govermentGst = govermentGst;
	}

	public void setGovermentGstPercent(int govermentGstPercent) {
		this.govermentGstPercent = govermentGstPercent;
	}

	public void setProfessionalFees(double professionalFees) {
		this.professionalFees = professionalFees;
	}

	public void setProfesionalGst(double profesionalGst) {
		this.profesionalGst = profesionalGst;
	}


	public void setServiceCharge(double serviceCharge) {
		this.serviceCharge = serviceCharge;
	}

	public void setServiceGst(double serviceGst) {
		this.serviceGst = serviceGst;
	}

	public void setServiceGstPercent(int serviceGstPercent) {
		this.serviceGstPercent = serviceGstPercent;
	}

	public void setOtherFees(double otherFees) {
		this.otherFees = otherFees;
	}

	public void setOtherGst(double otherGst) {
		this.otherGst = otherGst;
	}

	public void setOtherGstPercent(int otherGstPercent) {
		this.otherGstPercent = otherGstPercent;
	}

	public void setDocPersent(int docPersent) {
		this.docPersent = docPersent;
	}

	public void setFilingPersent(int filingPersent) {
		this.filingPersent = filingPersent;
	}

	public void setLiasoningPersent(int liasoningPersent) {
		this.liasoningPersent = liasoningPersent;
	}

	public void setCertificatePersent(int certificatePersent) {
		this.certificatePersent = certificatePersent;
	}

	public void setUploadReceipt(String uploadReceipt) {
		UploadReceipt = uploadReceipt;
	}

	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public void setPaymentDate(Date paymentDate) {
		this.paymentDate = paymentDate;
	}

	public void setEstimateNo(String estimateNo) {
		this.estimateNo = estimateNo;
	}

	public void setDoc(List<String> doc) {
		this.doc = doc;
	}

	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}

	public boolean isTdsPresent() {
		return tdsPresent;
	}

	public void setTdsPresent(boolean tdsPresent) {
		this.tdsPresent = tdsPresent;
	}

	public int getTdsPercent() {
		return tdsPercent;
	}

	public void setTdsPercent(int tdsPercent) {
		this.tdsPercent = tdsPercent;
	}

	public double getProfesionalGstFee() {
		return profesionalGstFee;
	}

	public void setProfesionalGstFee(double profesionalGstFee) {
		this.profesionalGstFee = profesionalGstFee;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmails() {
		return emails;
	}

	public void setEmails(String emails) {
		this.emails = emails;
	}

	public String getContactNo() {
		return contactNo;
	}

	public void setContactNo(String contactNo) {
		this.contactNo = contactNo;
	}

	public String getWhatsappNo() {
		return whatsappNo;
	}

	public void setWhatsappNo(String whatsappNo) {
		this.whatsappNo = whatsappNo;
	}

	public Long getCompanyId() {
		return companyId;
	}

	public void setCompanyId(Long companyId) {
		this.companyId = companyId;
	}

	public String getTermOfDelivery() {
		return termOfDelivery;
	}

	public void setTermOfDelivery(String termOfDelivery) {
		this.termOfDelivery = termOfDelivery;
	}

	public String getProviderType() {
		return providerType;
	}

	public void setProviderType(String providerType) {
		this.providerType = providerType;
	}

	public String getProductType() {
		return productType;
	}

	public void setProductType(String productType) {
		this.productType = productType;
	}

	public double getAmount() {
		return amount;
	}

	public void setAmount(double amount) {
		this.amount = amount;
	}

	public double getGstPercent() {
		return gstPercent;
	}

	public void setGstPercent(double gstPercent) {
		this.gstPercent = gstPercent;
	}

	public double getGstAmount() {
		return gstAmount;
	}

	public void setGstAmount(double gstAmount) {
		this.gstAmount = gstAmount;
	}

	public String getModeOfPayment() {
		return modeOfPayment;
	}

	public void setModeOfPayment(String modeOfPayment) {
		this.modeOfPayment = modeOfPayment;
	}

	public Date getReferenceDate() {
		return referenceDate;
	}

	public void setReferenceDate(Date referenceDate) {
		this.referenceDate = referenceDate;
	}

	public String getOtherReference() {
		return otherReference;
	}

	public void setOtherReference(String otherReference) {
		this.otherReference = otherReference;
	}

	public String getBuyerOrderNo() {
		return buyerOrderNo;
	}

	public void setBuyerOrderNo(String buyerOrderNo) {
		this.buyerOrderNo = buyerOrderNo;
	}

	public double getCgst() {
		return cgst;
	}

	public void setCgst(double cgst) {
		this.cgst = cgst;
	}

	public double getSgst() {
		return sgst;
	}

	public void setSgst(double sgst) {
		this.sgst = sgst;
	}

	public double getIgst() {
		return igst;
	}

	public void setIgst(double igst) {
		this.igst = igst;
	}

	public double getQuantity() {
		return quantity;
	}

	public void setQuantity(double quantity) {
		this.quantity = quantity;
	}

	
	
}
