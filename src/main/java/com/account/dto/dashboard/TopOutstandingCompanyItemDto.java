package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopOutstandingCompanyItemDto {

    private Long companyId;
    private String companyName;

    private BigDecimal outstandingAmount;

    private LocalDate earliestDueDate;
    private Long overdueDays;

    private Long unbilledCount;
}