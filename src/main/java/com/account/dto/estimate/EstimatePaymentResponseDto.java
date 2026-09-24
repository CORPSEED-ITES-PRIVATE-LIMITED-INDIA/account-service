package com.account.dto.estimate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimatePaymentResponseDto {

    private Long estimateId;

    private Long unbilledInvoiceId;

    private String unbilledNumber;

    private BigDecimal totalAmount;

    private BigDecimal receivedAmount;

    private BigDecimal outstandingAmount;

    private BigDecimal currentReceivedAmount;

    private Integer totalPaymentReceipts;

    private BigDecimal tdsPercentage;


    @Builder.Default
    private List<EstimatePaymentHistoryDto> paymentHistory =
            new ArrayList<>();
}