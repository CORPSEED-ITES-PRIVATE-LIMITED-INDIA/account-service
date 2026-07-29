package com.account.controller.internal;

import com.account.dto.vendor.VendorPaymentPostingRequestDto;
import com.account.dto.vendor.VendorPaymentPostingResponseDto;
import com.account.service.vendor.VendorPaymentPostingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/accountService/api/v1/internal/vendor-payments"
)
@RequiredArgsConstructor
public class InternalVendorPaymentController {

    private final VendorPaymentPostingService
            vendorPaymentPostingService;

    @PostMapping("/post")
    public ResponseEntity<VendorPaymentPostingResponseDto>
    postVendorPayment(
            @Valid
            @RequestBody
            VendorPaymentPostingRequestDto request
    ) {
        VendorPaymentPostingResponseDto response =
                vendorPaymentPostingService
                        .postVendorPayment(request);

        return ResponseEntity.ok(response);
    }
}