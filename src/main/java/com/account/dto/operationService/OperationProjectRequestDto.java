package com.account.dto.operationService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;


@Data
public class OperationProjectRequestDto {

    @NotBlank(message = "Project name cannot be empty")
    private String name;

    @NotBlank(message = "Project number cannot be empty")
    private String projectNo;

    @NotNull
    private Long salesPersonId;

    @NotBlank
    private String salesPersonName;

    @NotNull(message = "Product ID cannot be null")
    private Long productId;

    @NotNull(message = "Company ID cannot be null")
    private Long companyId;

    private String unbilledNumber;

    private String estimateNumber;

    @NotNull(message = "Contact ID cannot be null")
    private Long contactId;

    private Long leadId;

    private LocalDate date;

    @NotNull(message = "Total amount cannot be null")
    private Double totalAmount;

    @NotNull(message = "paid amount cannot be null")
    private Double paidAmount;

    @NotNull(message = "Payment type ID cannot be null")
    private Long paymentTypeId;

    @NotNull(message = "Approved by user ID cannot be null")
    private Long approvedById;

    @NotNull(message = "Created by user ID cannot be null")
    private Long createdBy;

    @NotNull(message = "Updated by user ID cannot be null")
    private Long updatedBy;

    private Long unitId;
}
