package com.account.dto.vendor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVendorSyncRequestDto {

    @NotNull(message = "Operation vendor ID is required")
    @Positive(message = "Operation vendor ID must be greater than zero")
    private Long operationVendorId;

    @Positive(
            message = "Vendor Accounts submission ID must be greater than zero"
    )
    private Long vendorAccountsSubmissionId;

    @Positive(
            message = "Vendor finalization ID must be greater than zero"
    )
    private Long vendorFinalizationId;

    @NotBlank(message = "Vendor name is required")
    @Size(
            max = 255,
            message = "Vendor name cannot exceed 255 characters"
    )
    private String vendorName;

    @Email(message = "Invalid vendor email")
    @Size(
            max = 255,
            message = "Email cannot exceed 255 characters"
    )
    private String email;

    @Size(
            max = 20,
            message = "Mobile cannot exceed 20 characters"
    )
    private String mobile;

    @Size(
            max = 20,
            message = "PAN cannot exceed 20 characters"
    )
    private String pan;

    @Size(
            max = 15,
            message = "GST number cannot exceed 15 characters"
    )
    private String gstNumber;

    /*
     * REGISTERED
     * UNREGISTERED
     * SEZ
     * INTERNATIONAL
     */
    private String gstRegistrationType;

    @Size(
            max = 255,
            message = "Account holder name cannot exceed 255 characters"
    )
    private String accountHolderName;

    @Size(
            max = 100,
            message = "Bank account number cannot exceed 100 characters"
    )
    private String bankAccountNumber;

    @Size(
            max = 20,
            message = "IFSC code cannot exceed 20 characters"
    )
    private String ifscCode;

    @Size(
            max = 255,
            message = "Bank name cannot exceed 255 characters"
    )
    private String bankName;

    @Size(
            max = 1000,
            message = "Branch address cannot exceed 1000 characters"
    )
    private String branchAddress;

    @Size(
            max = 1000,
            message = "Full address cannot exceed 1000 characters"
    )
    private String fullAddress;

    @Size(
            max = 100,
            message = "City cannot exceed 100 characters"
    )
    private String city;

    @Size(
            max = 100,
            message = "State cannot exceed 100 characters"
    )
    private String state;

    @Size(
            max = 100,
            message = "Country cannot exceed 100 characters"
    )
    private String country;

    @NotNull(message = "Vendor active status is required")
    private Boolean active;

    @Positive(
            message = "Approved user ID must be greater than zero"
    )
    private Long approvedByOperationUserId;

    private LocalDateTime approvedAt;

    private LocalDateTime operationUpdatedAt;

    /*
     * Optional voucher.
     *
     * Null:
     * Only vendor and vendor ledger are synchronized.
     *
     * Non-null:
     * Vendor, ledger and accounting voucher are created
     * in the same transaction.
     */
    @Valid
    private VendorVoucherRequestDto voucherDetails;
}