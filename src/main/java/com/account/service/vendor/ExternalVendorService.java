package com.account.service.vendor;

import com.account.dto.vendor.AccountVendorSyncRequestDto;
import com.account.dto.vendor.AccountVendorSyncResponseDto;

public interface ExternalVendorService {

    AccountVendorSyncResponseDto syncVendor(
            AccountVendorSyncRequestDto request
    );
}