package com.account.dto.dashboard;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class TopConvertedLeadItemDto {

    private Long invoiceId;
    private String invoiceNumber;

    private Long companyId;
    private String companyName;

    private Long unitId;
    private String unitName;

    private Long leadId;

    private Long solutionId;
    private String solutionName;

    private BigDecimal invoiceValue;
    private LocalDate invoiceDate;

    public TopConvertedLeadItemDto(
            Long invoiceId,
            String invoiceNumber,
            Long companyId,
            String companyName,
            Long unitId,
            String unitName,
            Long leadId,
            Long solutionId,
            String solutionName,
            BigDecimal invoiceValue,
            LocalDate invoiceDate
    ) {
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;

        this.companyId = companyId;
        this.companyName = companyName;

        this.unitId = unitId;
        this.unitName = unitName;

        this.leadId = leadId;

        this.solutionId = solutionId;
        this.solutionName = solutionName;

        this.invoiceValue = invoiceValue == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : invoiceValue.setScale(2, RoundingMode.HALF_UP);

        this.invoiceDate = invoiceDate;
    }
}