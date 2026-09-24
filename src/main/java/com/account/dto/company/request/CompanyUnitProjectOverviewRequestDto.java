package com.account.dto.company.request;


import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompanyUnitProjectOverviewRequestDto {

    @NotNull(message = "companyId is required")
    private Long companyId;

    @NotNull(message = "companyUnitId is required")
    private Long companyUnitId;
}