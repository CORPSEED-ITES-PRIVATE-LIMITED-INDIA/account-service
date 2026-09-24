package com.account.dto.invoice;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ConfirmInvoiceEInvoiceRequestDto {

    @NotNull(message = "User ID is required")
    private Long userId;

    @JsonProperty("eInvoiceAttachmentUrl")
    @JsonAlias({
            "einvoiceAttachmentUrl",
            "EInvoiceAttachmentUrl"
    })
    private String eInvoiceAttachmentUrl;

    @JsonProperty("eInvoiceIrn")
    @JsonAlias({
            "einvoiceIrn",
            "EInvoiceIrn"
    })
    private String eInvoiceIrn;

    @JsonProperty("eInvoiceAckNo")
    @JsonAlias({
            "einvoiceAckNo",
            "EInvoiceAckNo"
    })
    private String eInvoiceAckNo;

    @JsonProperty("eInvoiceAckDate")
    @JsonAlias({
            "einvoiceAckDate",
            "EInvoiceAckDate"
    })
    private LocalDateTime eInvoiceAckDate;

    private String remarks;
}