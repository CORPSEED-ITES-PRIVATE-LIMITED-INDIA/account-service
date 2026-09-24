package com.account.dto.payment;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PaymentLegalVerificationResponseDto {

    private Long id;

    private Long paymentReceiptId;
    private Long unbilledInvoiceId;
    private String unbilledNumber;

    private Long estimateId;
    private String estimateNumber;
    private String solutionName;

    private Long companyId;
    private String companyName;

    private Long unitId;
    private String unitName;

    private String poAttachmentUrl;
    private Integer paymentTermsDays;
    private String paymentTerms;

    private String status;

    private Long requestedById;
    private String requestedByName;

    private Long reviewedById;
    private String reviewedByName;

    private LocalDateTime reviewedAt;
    private String reviewRemark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}