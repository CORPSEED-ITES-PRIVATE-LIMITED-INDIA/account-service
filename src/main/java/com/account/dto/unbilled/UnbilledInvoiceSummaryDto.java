package com.account.dto.unbilled;

import com.account.domain.UnbilledStatus;
import jakarta.persistence.Column;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class UnbilledInvoiceSummaryDto {

    private Long id;
    private String unbilledNumber;
    private String advanceInvoiceNumber;
    private boolean advanceInvoiceFlag;
    private String estimateNumber;
    private Long estimateId;
    private String companyName;
    private String contactName;
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal outstandingAmount;
    private UnbilledStatus status;
    private LocalDateTime createdAt;
    private String createdByName;
    private LocalDateTime approvedAt;
    private String approvedByName;
    private Long solutionId;
    private String solutionName;
    private String name;
    private String emails;
    private String contactNo;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String country = "India";
    private String pinCode;
    private String gstNo;
    private Long searchCount;

}