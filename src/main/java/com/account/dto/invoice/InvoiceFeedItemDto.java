package com.account.dto.invoice;
import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class InvoiceFeedItemDto {

    private String recordType;          // "INVOICE" or "ADVANCE_REQUEST"

    private Long id;                    // invoice.id OR request.id
    private String publicUuid;

    private String referenceNumber;     // invoiceNumber OR request publicUuid
    private String estimateNumber;
    private String companyName;
    private String solutionName;

    private BigDecimal amount;          // grandTotal OR requestedAmount/approvedAmount
    private String currency;

    private String invoiceStatus;       // populated only when recordType = INVOICE
    private String advanceRequestStatus;// populated only when recordType = ADVANCE_REQUEST

    private String gstRegistrationType;

    private String createdByName;
    private LocalDateTime createdAt;

    private LocalDate invoiceDate;      // only for INVOICE rows
}
