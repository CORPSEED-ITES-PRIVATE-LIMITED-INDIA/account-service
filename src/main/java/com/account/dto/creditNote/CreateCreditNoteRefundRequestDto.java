package com.account.dto.creditNote;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateCreditNoteRefundRequestDto {

    private Long unbilledId;

    private Long createdByUserId;

    private BigDecimal refundAmount;

    private String reason;

    /*
     * Optional.
     * If not passed, system will attach all invoices against unbilled.
     */
    private List<Long> invoiceIds;
}