package com.account.controller.ledger;

import com.account.domain.ledger.VoucherSourceType;
import com.account.domain.ledger.VoucherStatus;
import com.account.domain.ledger.VoucherType;
import com.account.dto.ledger.AccountingVoucherRequestDto;
import com.account.dto.ledger.AccountingVoucherResponseDto;
import com.account.service.ledger.AccountingVoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/accountService/api/v1/accounting-vouchers")
@RequiredArgsConstructor
@Tag(name = "Accounting Vouchers", description = "APIs for accounting voucher posting")
public class AccountingVoucherController {

    private final AccountingVoucherService accountingVoucherService;

    @PostMapping
    @Operation(summary = "Create accounting voucher with debit/credit entries")
    public ResponseEntity<AccountingVoucherResponseDto> createVoucher(
            @Valid @RequestBody AccountingVoucherRequestDto request
    ) {
        AccountingVoucherResponseDto response = accountingVoucherService.createVoucher(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get accounting voucher by ID")
    public ResponseEntity<AccountingVoucherResponseDto> getVoucherById(
            @PathVariable Long id
    ) {
        AccountingVoucherResponseDto response = accountingVoucherService.getVoucherById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get paginated accounting vouchers")
    public ResponseEntity<Page<AccountingVoucherResponseDto>> getVouchers(
            @RequestParam(required = false) VoucherType voucherType,
            @RequestParam(required = false) VoucherSourceType sourceType,
            @RequestParam(required = false) VoucherStatus status,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<AccountingVoucherResponseDto> response = accountingVoucherService.getVouchers(
                voucherType,
                sourceType,
                status,
                fromDate,
                toDate,
                page - 1,
                size
        );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel posted accounting voucher")
    public ResponseEntity<Void> cancelVoucher(
            @PathVariable Long id,
            @RequestParam(required = false) String reason
    ) {
        accountingVoucherService.cancelVoucher(id, reason);
        return ResponseEntity.noContent().build();
    }
}