package com.account.dto.vendor;

import com.account.enm.VendorGSTRegistrationType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorResponseDto {

    private Long id;

    private Long operationVendorId;

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

    private LocalDateTime syncedAt;
}