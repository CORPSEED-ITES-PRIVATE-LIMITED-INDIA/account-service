package com.account.dashboard.domain;

import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

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

//@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
//@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")

@Table
@Entity
public class VendorPaymentRegister {

	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	
	Long id;
	private String serviceName;

	private Long leadId;
	private Long estimateId;
	
	
	
//	private String billingQuantity;//partial ,full ,milestone
	private String paymentType;//Sales,
	
	
	String name;
	String emails;
	String contactNo;
	String whatsappNo;

	@ManyToOne
    User createdBy;
	Date createDate;
	
	@ManyToOne
	User approvedBy;
	Date approveDate;
	
    
    Long businessArrangmentId;
    
    Long productCategoryId;
	
	String remarkByVendor;
	
	@ManyToMany(fetch = FetchType.EAGER,cascade = CascadeType.ALL)
	@JoinTable(name="vendor_payment_register_product_estimate",joinColumns = {@JoinColumn(name="vendor_payment_register_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="vendor_payment_register_product_estimate_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
	List<ProductEstimate> productEstimate;
	
//	private double serviceFees;
//	private double serviceGstAmount;
//	private int serviceGstPercent;
//	


    
//	private double totalAmount;
	private String remark;
	private Date paymentDate;
	private String estimateNo;
	String status; //initiate , approve , disapproved ,On hold
	


	String vendorCompanyName;
	String address;
	String city;
	String state;
	String country;
	String pinCode;
	
	String gstType;
	String gstNo;


	String updateDate;
	
	// add  attachment by Vendor team
	@ManyToMany(fetch = FetchType.EAGER,cascade = CascadeType.ALL)
	@JsonIgnore
	@JoinTable(name="vendor_payment_register_file_data",joinColumns = {@JoinColumn(name="vendor_payment_register_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="vendor_payment_register_file_data_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
	List<FileData>fileData;
	  
	String comment;
	
	@ManyToOne
	User createdByUser;
	
	
	private String transactionId;
	
	@ManyToOne 
	FileData paymentInvoice; // attachment By Account Team

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public Long getLeadId() {
		return leadId;
	}

	public void setLeadId(Long leadId) {
		this.leadId = leadId;
	}

	public Long getEstimateId() {
		return estimateId;
	}

	public void setEstimateId(Long estimateId) {
		this.estimateId = estimateId;
	}

	public String getPaymentType() {
		return paymentType;
	}

	public void setPaymentType(String paymentType) {
		this.paymentType = paymentType;
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

	public User getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
	}

	public User getApprovedBy() {
		return approvedBy;
	}

	public void setApprovedBy(User approvedBy) {
		this.approvedBy = approvedBy;
	}

	public Date getApproveDate() {
		return approveDate;
	}

	public void setApproveDate(Date approveDate) {
		this.approveDate = approveDate;
	}

	public String getRemarkByVendor() {
		return remarkByVendor;
	}

	public void setRemarkByVendor(String remarkByVendor) {
		this.remarkByVendor = remarkByVendor;
	}

	public List<ProductEstimate> getProductEstimate() {
		return productEstimate;
	}

	public void setProductEstimate(List<ProductEstimate> productEstimate) {
		this.productEstimate = productEstimate;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public Date getPaymentDate() {
		return paymentDate;
	}

	public void setPaymentDate(Date paymentDate) {
		this.paymentDate = paymentDate;
	}

	public String getEstimateNo() {
		return estimateNo;
	}

	public void setEstimateNo(String estimateNo) {
		this.estimateNo = estimateNo;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getVendorCompanyName() {
		return vendorCompanyName;
	}

	public void setVendorCompanyName(String vendorCompanyName) {
		this.vendorCompanyName = vendorCompanyName;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getPinCode() {
		return pinCode;
	}

	public void setPinCode(String pinCode) {
		this.pinCode = pinCode;
	}

	public String getGstType() {
		return gstType;
	}

	public void setGstType(String gstType) {
		this.gstType = gstType;
	}

	public String getGstNo() {
		return gstNo;
	}

	public void setGstNo(String gstNo) {
		this.gstNo = gstNo;
	}

	public String getUpdateDate() {
		return updateDate;
	}

	public void setUpdateDate(String updateDate) {
		this.updateDate = updateDate;
	}

	public List<FileData> getFileData() {
		return fileData;
	}

	public void setFileData(List<FileData> fileData) {
		this.fileData = fileData;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public User getCreatedByUser() {
		return createdByUser;
	}

	public void setCreatedByUser(User createdByUser) {
		this.createdByUser = createdByUser;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public FileData getPaymentInvoice() {
		return paymentInvoice;
	}

	public void setPaymentInvoice(FileData paymentInvoice) {
		this.paymentInvoice = paymentInvoice;
	}

	public Long getBusinessArrangmentId() {
		return businessArrangmentId;
	}

	public void setBusinessArrangmentId(Long businessArrangmentId) {
		this.businessArrangmentId = businessArrangmentId;
	}

	public Long getProductCategoryId() {
		return productCategoryId;
	}

	public void setProductCategoryId(Long productCategoryId) {
		this.productCategoryId = productCategoryId;
	}
	


	
}
