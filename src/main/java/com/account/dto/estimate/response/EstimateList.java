package com.account.dto.estimate.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstimateList {

    // Internal ID - used only internally (not exposed in public views)
    private Long id;

    // Secure public identifier for sharing links/PDF/email/WhatsApp
    private String publicUuid;  // UUID v4 - safe for public exposure

    private Long leadId;

    // Human-readable estimate number
    private String estimateNumber;

    private LocalDate estimateDate;
    private LocalDate validUntil;
    // Financial breakdown
    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;

    private String solutionName;           // e.g. "FSSAI State License - Uttar Pradesh"
    private String solutionType;           // SERVICE, PRODUCT, PLANT_SETUP
    private String status;                 // DRAFT, SENT_TO_CLIENT, APPROVED, etc.

    // Audit fields
    private LocalDateTime createdAt;
    private Long createdById;              // ID of user who created it
    private String createdByName;          // Optional - full name for display

    private Long companyId;
    private String name;
    private String panNo;




}
