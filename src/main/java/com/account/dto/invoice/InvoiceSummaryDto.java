package com.account.dto.invoice;

import com.account.domain.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class InvoiceSummaryDto {

    private Long id;
    private String publicUuid;
    private String invoiceNumber;
    private String unbilledNumber;
    private String estimateNumber;

    private Long paymentTypeId;
    private String paymentTypeCode;

    private Long estimateId;
    private Long solutionId;
    private String solutionName;
    private String solutionType;

    private String companyName;
    private String contactName;

    private LocalDate invoiceDate;
    private BigDecimal grandTotal;
    private BigDecimal totalGstAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;

    private String irn;
    private InvoiceStatus status;

    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}