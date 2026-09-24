package com.account.service.vendor;

import com.account.dto.vendor.VendorPaymentPostingRequestDto;
import com.account.dto.vendor.VendorPaymentPostingResponseDto;

public interface VendorPaymentPostingService {

    VendorPaymentPostingResponseDto postVendorPayment(
            VendorPaymentPostingRequestDto request
    );
}