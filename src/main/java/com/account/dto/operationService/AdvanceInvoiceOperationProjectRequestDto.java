package com.account.dto.operationService;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.invoice.InvoiceOrigin;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdvanceInvoiceOperationProjectRequestDto {

    private String idempotencyKey;

    private Long estimateId;
    private String estimateNumber;
    private Long leadId;

    private Long companyId;
    private String companyName;
    private Long unitId;
    private String unitName;
    private Long contactId;

    private Long solutionId;
    private String solutionName;

    private Long invoiceId;
    private String invoiceNumber;
    private InvoiceOrigin invoiceOrigin;
    private LocalDate invoiceDate;

    private BigDecimal taxableAmount;
    private BigDecimal gstAmount;
    private BigDecimal grandTotal;

    private GstRegistrationType gstRegistrationType;
    private boolean eInvoiceRequired;
    private String eInvoiceIrn;
    private String eInvoiceAckNo;
    private LocalDateTime eInvoiceAckDate;

    private Long confirmedByUserId;
}
