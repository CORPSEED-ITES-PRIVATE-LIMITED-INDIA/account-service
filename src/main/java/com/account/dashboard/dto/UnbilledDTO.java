package com.account.dashboard.dto;


import java.util.Date;
import java.util.List;

public class UnbilledDTO {

	private Date date;
	private Long project;
	private Long estimateId;
	private List<Long> invoiced;
	private String client;
	private String company;
	private double txnAmount;
	private double orderAmount;
	private double dueAmount;
	private double paidAmount;
	private String status;

	// Getters & setters

	public Date getDate() { return date; }
	public void setDate(Date date) { this.date = date; }

	public Long getProject() { return project; }
	public void setProject(Long project) { this.project = project; }

	public Long getEstimateId() { return estimateId; }
	public void setEstimateId(Long estimateId) { this.estimateId = estimateId; }

    

	public List<Long> getInvoiced() {
		return invoiced;
	}
	public void setInvoiced(List<Long> invoiced) {
		this.invoiced = invoiced;
	}
	public String getClient() { return client; }
	public void setClient(String client) { this.client = client; }

	public String getCompany() { return company; }
	public void setCompany(String company) { this.company = company; }

	public double getTxnAmount() { return txnAmount; }
	public void setTxnAmount(double txnAmount) { this.txnAmount = txnAmount; }

	public double getOrderAmount() { return orderAmount; }
	public void setOrderAmount(double orderAmount) { this.orderAmount = orderAmount; }

	public double getDueAmount() { return dueAmount; }
	public void setDueAmount(double dueAmount) { this.dueAmount = dueAmount; }

	public double getPaidAmount() { return paidAmount; }
	public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }

}
