package com.account.dto.invoice;

import com.account.domain.invoice.AdvanceTaxInvoiceRequestStatus;
import com.account.domain.invoice.InvoicePaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdvanceTaxInvoiceResponseDto {

    private Long requestId;
    private String publicUuid;

    private Long estimateId;
    private String estimateNumber;
    private BigDecimal estimateGrandTotal;

    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private AdvanceTaxInvoiceRequestStatus requestStatus;

    private Long requestedByUserId;
    private String requestedByName;

    private Long reviewedByUserId;
    private String reviewedByName;

    private Long invoiceId;
    private String invoiceNumber;
    private BigDecimal invoiceGrandTotal;

    private BigDecimal receivedAmount;
    private BigDecimal pendingReceivedAmount;
    private BigDecimal availableOutstandingAmount;
    private BigDecimal outstandingAmount;
    private InvoicePaymentStatus invoicePaymentStatus;

    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    private String message;
}
