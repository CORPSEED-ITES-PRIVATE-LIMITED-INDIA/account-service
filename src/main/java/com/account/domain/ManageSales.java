package com.account.domain;

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

    double govermentfees;
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

	


}
