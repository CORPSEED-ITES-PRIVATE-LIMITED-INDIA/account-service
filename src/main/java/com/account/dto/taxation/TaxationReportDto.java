package com.account.dto.taxation;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaxationReportDto {

    private TaxationReportType type;

    private GstTaxationReportDto gstReport;

    private TdsTaxationReportDto tdsReport;
}