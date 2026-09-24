package com.account.service;

import com.account.dto.vendor.VendorResponseDto;
import com.account.dto.vendor.VendorSyncRequestDto;

public interface VendorService {

    VendorResponseDto syncVendor(VendorSyncRequestDto request);
}
