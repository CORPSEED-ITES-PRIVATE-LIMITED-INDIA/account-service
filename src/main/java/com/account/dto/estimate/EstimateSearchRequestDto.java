package com.account.dto.estimate;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EstimateSearchRequestDto {

    // Free text search (estimate number, solution name, customer name, etc.)
    private String query;

    // Exact / partial filters
    private Long companyId;
    private Long unitId;
    private Long contactId;
    private Long leadId;
    private String estimateNumber;
    private String status;           // DRAFT, SENT, APPROVED, REJECTED, etc.
    private String solutionType;     // enum name

    // Date range
    private LocalDate createdFrom;
    private LocalDate createdTo;
    private LocalDate validUntilFrom;
    private LocalDate validUntilTo;

    // Amount range (optional - can be very useful)
    private BigDecimal minGrandTotal;
    private BigDecimal maxGrandTotal;

    // Pagination & sorting
    private int page = 0;          // 0-based
    private int size = 20;
    private String sortBy = "createdAt";   // createdAt, estimateNumber, grandTotal, etc.
    private String sortDirection = "DESC";
}