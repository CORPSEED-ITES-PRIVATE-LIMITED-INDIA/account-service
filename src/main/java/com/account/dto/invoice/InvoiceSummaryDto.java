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
    private String publicUuid;           // safer than exposing internal id
    private String invoiceNumber;
    private String unbilledNumber;
    private String estimateNumber;
    private Long solutionId;
    private String solutionName;
    private String solutionType;   // optional — if you want to show type/icon
    private String companyName;
    private String contactName;
    private LocalDate invoiceDate;
    private BigDecimal grandTotal;
    private BigDecimal totalGstAmount;
    private String irn;                  // null if not e-invoiced yet
    private InvoiceStatus status;        // using your enum
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;        // optional – if you track SENT_TO_CLIENT time
}