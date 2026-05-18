package com.account.dto.creditNote;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateCreditNoteRefundRequestDto {

    private Long unbilledId;

    private String estimateNumber;

    private Long createdByUserId;

    private BigDecimal refundAmount;

    private String reason;

    private List<Long> invoiceIds;
}