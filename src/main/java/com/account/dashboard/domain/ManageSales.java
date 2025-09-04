package com.account.dashboard.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Table
@Entity
@Getter
@Setter
@Data
public class ManageSales {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	Long projectId;
	Long clientId;
	String estimateNo;
	@ManyToOne
	User createdBy;
	@ManyToOne
	User updatedBy;
    @ManyToMany(cascade = CascadeType.ALL)
    @JsonIgnore
	@JoinTable(name="manage_sales_amount",joinColumns = {@JoinColumn(name="manage_sales_id",referencedColumnName="id",nullable=true)},
			inverseJoinColumns = {@JoinColumn(name="manage_sales_amount_id"
					+ "",referencedColumnName = "id",nullable=true,unique=false)})
    private List<Amount>manageSalesAmount;

    double  govermentfees;
	String govermentCode;
	double govermentGst;
	double professionalFees;
	String professionalCode;
	double profesionalGst;
	double serviceCharge;
	String serviceCode;
	double serviceGst;
	double otherFees;
	double totalAmount;
	double paidAmount;	
	String otherCode;
	double otherGst;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Long getProjectId() {
		return projectId;
	}
	public void setProjectId(Long projectId) {
		this.projectId = projectId;
	}
	public Long getClientId() {
		return clientId;
	}
	public void setClientId(Long clientId) {
		this.clientId = clientId;
	}
	public String getEstimateNo() {
		return estimateNo;
	}
	public void setEstimateNo(String estimateNo) {
		this.estimateNo = estimateNo;
	}
	public User getCreatedBy() {
		return createdBy;
	}
	public void setCreatedBy(User createdBy) {
		this.createdBy = createdBy;
	}
	public User getUpdatedBy() {
		return updatedBy;
	}
	public void setUpdatedBy(User updatedBy) {
		this.updatedBy = updatedBy;
	}
	public List<Amount> getManageSalesAmount() {
		return manageSalesAmount;
	}
	public void setManageSalesAmount(List<Amount> manageSalesAmount) {
		this.manageSalesAmount = manageSalesAmount;
	}
	public double getGovermentfees() {
		return govermentfees;
	}
	public void setGovermentfees(double govermentfees) {
		this.govermentfees = govermentfees;
	}
	public String getGovermentCode() {
		return govermentCode;
	}
	public void setGovermentCode(String govermentCode) {
		this.govermentCode = govermentCode;
	}
	public double getGovermentGst() {
		return govermentGst;
	}
	public void setGovermentGst(double govermentGst) {
		this.govermentGst = govermentGst;
	}
	public double getProfessionalFees() {
		return professionalFees;
	}
	public void setProfessionalFees(double professionalFees) {
		this.professionalFees = professionalFees;
	}
	public String getProfessionalCode() {
		return professionalCode;
	}
	public void setProfessionalCode(String professionalCode) {
		this.professionalCode = professionalCode;
	}
	public double getProfesionalGst() {
		return profesionalGst;
	}
	public void setProfesionalGst(double profesionalGst) {
		this.profesionalGst = profesionalGst;
	}
	public double getServiceCharge() {
		return serviceCharge;
	}
	public void setServiceCharge(double serviceCharge) {
		this.serviceCharge = serviceCharge;
	}
	public String getServiceCode() {
		return serviceCode;
	}
	public void setServiceCode(String serviceCode) {
		this.serviceCode = serviceCode;
	}
	public double getServiceGst() {
		return serviceGst;
	}
	public void setServiceGst(double serviceGst) {
		this.serviceGst = serviceGst;
	}
	public double getOtherFees() {
		return otherFees;
	}
	public void setOtherFees(double otherFees) {
		this.otherFees = otherFees;
	}
	public double getTotalAmount() {
		return totalAmount;
	}
	public void setTotalAmount(double totalAmount) {
		this.totalAmount = totalAmount;
	}
	public double getPaidAmount() {
		return paidAmount;
	}
	public void setPaidAmount(double paidAmount) {
		this.paidAmount = paidAmount;
	}
	public String getOtherCode() {
		return otherCode;
	}
	public void setOtherCode(String otherCode) {
		this.otherCode = otherCode;
	}
	public double getOtherGst() {
		return otherGst;
	}
	public void setOtherGst(double otherGst) {
		this.otherGst = otherGst;
	}
	
	
	


}
