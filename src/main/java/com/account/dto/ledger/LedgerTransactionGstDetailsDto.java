package com.account.dto.ledger;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionGstDetailsDto {

    private String gstNo;

    private BigDecimal subTotalExGst;
    private BigDecimal totalGstAmount;

    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;

    private BigDecimal grandTotal;
}