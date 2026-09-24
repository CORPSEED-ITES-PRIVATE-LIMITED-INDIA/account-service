package com.account.dto.invoice;

import com.account.domain.company.GstRegistrationType;
import com.account.domain.invoice.InvoiceOrigin;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ConfirmAdvanceInvoiceResponseDto {

    private Long invoiceId;
    private String invoiceNumber;
    private InvoiceOrigin invoiceOrigin;
    private GstRegistrationType gstRegistrationType;

    private boolean eInvoiceRequired;
    private boolean eInvoiceConfirmed;

    private String eInvoiceIrn;
    private String eInvoiceAckNo;
    private LocalDateTime eInvoiceAckDate;

    private boolean salesVoucherPosted;
    private boolean operationSynced;
    private String operationProjectNo;
    private String operationSyncStatus;
    private String message;
}
