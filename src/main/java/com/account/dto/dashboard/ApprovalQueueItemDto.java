package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalQueueItemDto {

    private Long itemId;

    // UNBILLED_INVOICE_APPROVAL, CANCEL_REQUEST,
    // PAYMENT_RECEIPT_VERIFICATION, TDS_REGISTRATION
    private String itemType;

    private String title;
    private String subTitle;

    private Long companyId;
    private String companyName;

    private Long referenceId;
    private String referenceNumber;

    private BigDecimal amount;

    private String sourceStatus;

    // Pending, Urgent, Review
    private String badge;

    // PENDING, URGENT, REVIEW
    private String priority;

    private LocalDateTime createdAt;
}