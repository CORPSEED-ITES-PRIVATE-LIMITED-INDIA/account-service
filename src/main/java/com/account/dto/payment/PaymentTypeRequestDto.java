package com.account.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentTypeRequestDto {

    private Long id; // only used in update – ignored in create

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private Boolean active;

    @NotNull(message = "createdBy is required")
    private Long createdBy;   // only for create

    @NotNull(message = "updatedBy is required")
    private Long updatedBy;   // for both create & update
}