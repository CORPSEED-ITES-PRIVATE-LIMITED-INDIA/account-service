package com.account.dto.payment;

import lombok.Data;

@Data
public class PaymentTypeResponseDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private boolean active;
}