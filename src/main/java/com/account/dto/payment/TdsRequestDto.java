package com.account.dto.payment;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TdsRequestDto {

    private BigDecimal tdsPercentage; // allowed: 2 or 10
}