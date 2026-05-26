package com.account.dto.operationService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcurementPurchaseOrderActionRequestDto {

    private String comment;
    private String reason;
}