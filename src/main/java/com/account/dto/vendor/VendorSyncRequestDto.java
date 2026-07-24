package com.account.dto.vendor;

import com.account.enm.VendorGSTRegistrationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorSyncRequestDto {

    @NotNull(message = "Operation vendor ID is required")
    private Long operationVendorId;

    @NotBlank(message = "Vendor name is required")
    private String vendorName;

    private String email;

    private String mobile;

    private String pan;

    private String gstNumber;

    private VendorGSTRegistrationType gstRegistrationType;

    private String accountHolderName;

    private String bankAccountNumber;

    private String bankName;

    private String ifscCode;

    private Boolean active;

    private LocalDateTime operationUpdatedAt;
}