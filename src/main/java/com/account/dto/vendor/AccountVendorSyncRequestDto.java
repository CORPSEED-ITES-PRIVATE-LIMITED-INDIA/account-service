package com.account.dto.vendor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Vendor synchronization envelope sent from Operation Service to Account Service.
 *
 * Used for two purposes:
 *   1. Vendor onboarding sync (paymentApproval == null) — creates/updates the
 *      vendor party ledger only.
 *   2. Procurement invoice/payment posting (paymentApproval != null) — creates
 *      the PURCHASE_INVOICE and PAYMENT vouchers from the immutable snapshot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVendorSyncRequestDto {

    // Identity
    private Long operationVendorId;
    private Long vendorAccountsSubmissionId;
    private Long vendorFinalizationId;

    // Vendor master details
    private String vendorName;
    private String email;
    private String mobile;
    private String pan;
    private String gstNumber;
    private String gstRegistrationType;

    // Bank details (vendor's own bank; not the paying bank ledger)
    private String accountHolderName;
    private String bankAccountNumber;
    private String ifscCode;
    private String bankName;
    private String branchAddress;

    // Address
    private String fullAddress;
    private String city;
    private String state;
    private String country;

    // Status / audit
    private Boolean active;
    private Long approvedByOperationUserId;
    private LocalDateTime approvedAt;
    private LocalDateTime operationUpdatedAt;

    /**
     * Null for vendor onboarding.
     * Non-null for procurement invoice/payment posting.
     */
    private VendorPaymentApprovalRequestDto paymentApproval;
}
