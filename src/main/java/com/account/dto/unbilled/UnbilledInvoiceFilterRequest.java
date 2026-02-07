package com.account.dto.unbilled;

import com.account.domain.UnbilledStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class UnbilledInvoiceFilterRequest {

    private String search;
    private UnbilledStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    private BigDecimal minOutstandingAmount;
    private BigDecimal maxOutstandingAmount;
}
