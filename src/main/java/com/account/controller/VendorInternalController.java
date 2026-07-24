package com.account.controller;


import com.account.dto.vendor.VendorResponseDto;
import com.account.dto.vendor.VendorSyncRequestDto;
import com.account.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accountService/api/internal/vendors")
@RequiredArgsConstructor
public class VendorInternalController {

    private final VendorService vendorService;

    @PostMapping("/sync")
    public ResponseEntity<VendorResponseDto> syncVendor(
            @Valid @RequestBody VendorSyncRequestDto request
    ) {
        return ResponseEntity.ok(
                vendorService.syncVendor(request)
        );
    }
}