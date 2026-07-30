package com.account.controller.vendor;

import com.account.dto.vendor.AccountVendorSyncRequestDto;
import com.account.dto.vendor.AccountVendorSyncResponseDto;
import com.account.service.vendor.ExternalVendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Internal External Vendor",
        description = """
                Internal APIs for synchronizing Operation Service vendors,
                vendor ledgers and approved procurement vendor invoices.
                """
)
@RestController
@RequestMapping(
        "/accountService/api/v1/internal/vendors"
)
@RequiredArgsConstructor
public class InternalExternalVendorController {

    private final ExternalVendorService externalVendorService;

    @PostMapping("/sync")
    @Operation(
            summary = """
                    Create/update external vendor and vendor ledger.
                    When paymentApproval is present, calculate GST/TDS
                    and create the purchase accounting voucher.
                    """
    )
    public ResponseEntity<AccountVendorSyncResponseDto> syncVendor(
            @Valid
            @RequestBody
            AccountVendorSyncRequestDto request
    ) {
        return ResponseEntity.ok(
                externalVendorService.syncVendor(request)
        );
    }
}