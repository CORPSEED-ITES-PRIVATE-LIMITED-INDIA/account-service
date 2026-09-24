package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceStatusOverviewItemDto {

    private String status;
    private Long count;
    private Double percentage;
}