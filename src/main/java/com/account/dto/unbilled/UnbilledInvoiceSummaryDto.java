package com.account.dto.unbilled;

import com.account.domain.UnbilledStatus;
import com.account.dto.payment.TdsResponseDto;
import jakarta.persistence.Column;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class UnbilledInvoiceSummaryDto {

    private Long id;
    private Long leadId;
    private String unbilledNumber;
    private String advanceInvoiceNumber;
    private boolean advanceInvoiceFlag;
    private boolean governmentFeeActiveFlag = false;
    private boolean tdsActiveFlag;
    private String estimateNumber;
    private Long estimateId;
    private Long paymentTypeId;
    private String paymentTypeCode;


    private String companyName;
    private Long companyId;
    private String companyStatus;

    private Long unitId;
    private String unitName;
    private String unitStatus;

    private String contactName;
    private BigDecimal totalAmount;
    private BigDecimal receivedAmount;
    private BigDecimal currentReceivedAmount;
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

    private Long paymentReceiptId;
    private String paymentProof;
    private String transactionReference;
    private String paymentMode;
    private LocalDate paymentDate;
    private BigDecimal paymentAmount;
    private String paymentStatus;
    private TdsResponseDto tdsResponseDto;

    private String cancelAttachment;
    private String rejectionReason;



}