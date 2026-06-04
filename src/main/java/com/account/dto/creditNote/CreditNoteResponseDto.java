package com.account.dto.creditNote;

import com.account.domain.creditNote.CreditNoteStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CreditNoteResponseDto {

    private Long id;

    private String creditNoteNumber;

    private Long proposalId;

    private Long unbilledId;

    private String unbilledNumber;

    private Long estimateId;

    private String estimateNumber;
    private String attachment;



    private Long companyId;

    private String companyName;

    private Long contactId;

    private String contactName;

    private BigDecimal totalAmount;

    private BigDecimal receivedAmount;

    private BigDecimal currentReceivedAmount;

    private BigDecimal outstandingAmount;

    private BigDecimal refundAmount;
    private BigDecimal creditAmount;

    private CreditNoteStatus status;

    private String reason;

    private String approvalRemarks;

    private Long createdById;

    private Long approvedById;

    private LocalDateTime createdAt;

    private LocalDateTime approvedAt;

    private String rejectionReason;

    private Long rejectedById;

    private LocalDateTime rejectedAt;

    private LocalDateTime updatedAt;

    private List<CreditNoteInvoiceDetailResponseDto> invoices;
}