package com.account.dto.creditNote;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreateCreditNoteRefundRequestDto {

    private Long unbilledId;

    private String estimateNumber;

    private Long createdByUserId;

    private BigDecimal refundAmount;

    private String attachment;

    private String reason;

    private List<Long> invoiceIds;

}