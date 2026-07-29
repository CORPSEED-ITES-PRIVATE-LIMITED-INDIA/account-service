package com.account.dto.vendor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    private Long vendorAccountsSubmissionId;

    private Long vendorFinalizationId;

    @NotBlank(message = "Vendor name is required")
    private String vendorName;

    private String email;

    private String mobile;

    private String pan;

    private String gstNumber;

    /*
     * REGISTERED
     * UNREGISTERED
     * SEZ
     * INTERNATIONAL
     */
    private String gstRegistrationType;

    private String accountHolderName;

    private String bankAccountNumber;

    private String ifscCode;

    private String bankName;

    private String branchAddress;

    private String fullAddress;

    private String city;

    private String state;

    private String country;

    @NotNull(message = "Vendor active status is required")
    private Boolean active;

    private Long approvedByOperationUserId;

    private LocalDateTime approvedAt;

    private LocalDateTime operationUpdatedAt;
}