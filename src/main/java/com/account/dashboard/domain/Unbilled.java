package com.account.dashboard.domain;

import jakarta.persistence.*;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "unbilled")
public class Unbilled {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Temporal(TemporalType.DATE)
    private Date date;

    private Long project;

    private Long estimateId;

    @OneToMany(mappedBy = "unbilled", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceData> invoiced;

    private String client;

    private String company;

    private double txnAmount;

    private double orderAmount;

    private double dueAmount;

    private double paidAmount;

    private String status;

    // Getters & setters omitted for brevity
    // Generate all standard getters and setters here
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public Long getProject() { return project; }
    public void setProject(Long project) { this.project = project; }

    public Long getEstimateId() { return estimateId; }
    public void setEstimateId(Long estimateId) { this.estimateId = estimateId; }



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