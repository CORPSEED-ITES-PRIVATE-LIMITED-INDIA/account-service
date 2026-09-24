package com.account.dto.estimate;

import com.account.domain.status.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimatePaymentHistoryDto {

    private Long paymentReceiptId;

    private BigDecimal amount;

    private BigDecimal allocatedAmount;

    private BigDecimal unallocatedAmount;

    private LocalDate paymentDate;

    private String paymentMode;

    private String transactionReference;

    private PaymentStatus status;

    private String remarks;

    private String paymentProof;

    private String paymentTerms;

    private Integer paymentTermsDays;

    private Long paymentTypeId;

    private Long receivedByUserId;

    private Long bankLedgerId;

    private String poNumber;

    private String poAttachmentUrl;

    private String eprCertificateOrInvoiceNumber;

    private String eprFinancialYear;

    private String eprPortalRegistrationNumber;

    private LocalDateTime createdAt;
}