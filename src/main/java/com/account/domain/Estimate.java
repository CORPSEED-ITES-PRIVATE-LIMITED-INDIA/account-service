package com.account.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "estimate")
@Getter
@Setter
public class Estimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique order/estimate number (e.g., EST-2026-0001)
    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;

    // Date when the estimate was requested / created
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    // Product/Service name
    @Column(name = "product_name", nullable = false)
    private String productName;

    // Client Company (mandatory)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // Specific unit/location of the company (optional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_id")
    private CompanyUnit unit;

    // Primary contact person for this estimate
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    // Billing Address
    @Column(name = "billing_address_line1")
    private String billingAddressLine1;

    @Column(name = "billing_address_line2")
    private String billingAddressLine2;

    @Column(name = "billing_city")
    private String billingCity;

    @Column(name = "billing_state")
    private String billingState;

    @Column(name = "billing_country")
    private String billingCountry = "India";

    @Column(name = "billing_pin_code")
    private String billingPinCode;

    // Shipping / Service Delivery Address
    @Column(name = "shipping_address_line1")
    private String shippingAddressLine1;

    @Column(name = "shipping_address_line2")
    private String shippingAddressLine2;

    @Column(name = "shipping_city")
    private String shippingCity;

    @Column(name = "shipping_state")
    private String shippingState;

    @Column(name = "shipping_country")
    private String shippingCountry = "India";

    @Column(name = "shipping_pin_code")
    private String shippingPinCode;

    // Remarks / Notes
    @Column(name = "remark", columnDefinition = "TEXT")
    private String remark;

    // Employee code for internal discount
    @Column(name = "employee_code")
    private String employeeCode;

    // Pricing fields - using BigDecimal for precise monetary values
    @Column(name = "base_price", precision = 15, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(name = "tax", precision = 15, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "government_fee", precision = 15, scale = 2)
    private BigDecimal governmentFee = BigDecimal.ZERO;

    @Column(name = "cgst", precision = 15, scale = 2)
    private BigDecimal cgst = BigDecimal.ZERO;

    @Column(name = "sgst", precision = 15, scale = 2)
    private BigDecimal sgst = BigDecimal.ZERO;

    @Column(name = "service_charge", precision = 15, scale = 2)
    private BigDecimal serviceCharge = BigDecimal.ZERO;

    @Column(name = "professional_fee", precision = 15, scale = 2)
    private BigDecimal professionalFee = BigDecimal.ZERO;

    @Column(name = "total_price", precision = 15, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    // Status of the estimate
    @Column(name = "status")
    private String status = "Draft";

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    // Auditing fields
    @CreatedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @LastModifiedBy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        isDeleted = false;
    }

    /**
     * Recalculates the total price by summing all individual price components.
     * Call this method before saving if any price field has been modified.
     */
    public void calculateTotal() {
        this.totalPrice = basePrice
                .add(tax)
                .add(governmentFee)
                .add(cgst)
                .add(sgst)
                .add(serviceCharge)
                .add(professionalFee);
    }
}