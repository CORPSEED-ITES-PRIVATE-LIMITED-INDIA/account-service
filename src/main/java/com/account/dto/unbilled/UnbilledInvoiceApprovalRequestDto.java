package com.account.dto.unbilled;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UnbilledInvoiceApprovalRequestDto {

    @NotNull(message = "Approver user ID is required")
    private Long approverUserId;

    @NotBlank(message = "Approval remarks are required")
    private String approvalRemarks;

    // Optional: rejection reason if you want to support reject too
    private String rejectionReason;
}