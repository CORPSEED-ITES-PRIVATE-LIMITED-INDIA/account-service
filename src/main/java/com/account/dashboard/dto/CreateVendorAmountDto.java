package com.account.dashboard.dto;

import java.util.Date;
import java.util.List;

import com.account.dashboard.domain.FileData;
import com.account.dashboard.domain.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;

public class CreateVendorAmountDto {
	
	private String serviceName;
	private Long leadId;
	private Long estimateId;
	private String quantity;	
	String name;
	String emails;
	String contactNo;
	String whatsappNo;

	
    Long createdById;
	Date createDate;
	
	Long approvedBy;
	Date approveDate;
	
	String remarkByVendor;
	
//	private double serviceFees;
//	private double serviceGstAmount;
//	private int serviceGstPercent;
//	
//	boolean tdsPresent;
//	private double tdsAmount;
//	private int tdsPercent;

    
//	private double totalAmount;
	
	List<CreateVendorSubDto> CreateVendorSubDto;
	private String remark;
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
	
	// add  attachment by Vendor team
	List<Long>fileData;
	
    Long businessArrangmentId;
    
    Long productCategoryId;



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

	public String getQuantity() {
		return quantity;
	}

	public void setQuantity(String quantity) {
		this.quantity = quantity;
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

	

	public Long getCreatedById() {
		return createdById;
	}

	public void setCreatedById(Long createdById) {
		this.createdById = createdById;
	}

	public Date getCreateDate() {
		return createDate;
	}

	public void setCreateDate(Date createDate) {
		this.createDate = createDate;
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

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
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

	public String getGstNo() {
		return gstNo;
	}

	public void setGstNo(String gstNo) {
		this.gstNo = gstNo;
	}

	public String getGstType() {
		return gstType;
	}

	public void setGstType(String gstType) {
		this.gstType = gstType;
	}

	public List<Long> getFileData() {
		return fileData;
	}

	public void setFileData(List<Long> fileData) {
		this.fileData = fileData;
	}

	public Long getApprovedBy() {
		return approvedBy;
	}

	public void setApprovedBy(Long approvedBy) {
		this.approvedBy = approvedBy;
	}

	public List<CreateVendorSubDto> getCreateVendorSubDto() {
		return CreateVendorSubDto;
	}

	public void setCreateVendorSubDto(List<CreateVendorSubDto> createVendorSubDto) {
		CreateVendorSubDto = createVendorSubDto;
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
