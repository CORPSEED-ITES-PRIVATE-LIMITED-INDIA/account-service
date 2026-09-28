package com.account.dto.unbilled;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class UnbilledInvoiceApprovalResponseDto {

    private String name;

    private String projectNo;

    private Long salesPersonId;

    private String salesPersonName;

    private Long productId;

    private Long companyId;
    private Long companyUnitId;


    private String unbilledNumber;
    private String advanceInvoiceNumber;

    private boolean advanceInvoiceFlag;

    private String estimateNumber;

    private Long contactId;

    private Long leadId;

    private LocalDate date;

    private String address;

    private String city;

    private String state;

    private String country;

    private String primaryPinCode;

    private Double totalAmount;

    private Double paidAmount;

    private boolean refundIssued;
    private BigDecimal refundAmount;
    private String refundReason;
    private String refundAttachment;
    private LocalDateTime refundedAt;
    private String refundedByName;

    private Long paymentTypeId;

    private Long approvedById;

    private Long createdBy;

    private Long updatedBy;
}