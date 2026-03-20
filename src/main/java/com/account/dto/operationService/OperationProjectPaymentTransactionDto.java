package com.account.dto.operationService;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.Date;

@Data
public class OperationProjectPaymentTransactionDto {

    private Double amount;
    private Date paymentDate;
    private Long createdBy;
}
