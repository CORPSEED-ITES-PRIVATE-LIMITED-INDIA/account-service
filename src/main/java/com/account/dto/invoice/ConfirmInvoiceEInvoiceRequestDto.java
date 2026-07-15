package com.account.dto.invoice;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ConfirmInvoiceEInvoiceRequestDto {

    @NotNull(message = "User ID is required")
    private Long userId;

    private String eInvoiceAttachmentUrl;

    private String eInvoiceIrn;

    private String eInvoiceAckNo;

    private LocalDateTime eInvoiceAckDate;

    private String remarks;
}