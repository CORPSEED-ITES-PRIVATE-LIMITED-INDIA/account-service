package com.account.controller.ledger;

import com.account.domain.ledger.DebitCredit;
import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerType;
import com.account.dto.ledger.LedgerMasterRequestDto;
import com.account.dto.ledger.LedgerMasterResponseDto;
import com.account.dto.ledger.LedgerStatementResponseDto;
import com.account.service.ledger.LedgerMasterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/accountService/api/v1/ledgers")
@RequiredArgsConstructor
@Tag(name = "Ledger Master", description = "APIs for ledger master")

public class LedgerMasterController {

    private final LedgerMasterService ledgerMasterService;

    @PostMapping
    @Operation(summary = "Create ledger")
    public ResponseEntity<LedgerMasterResponseDto> createLedger(
            @Valid @RequestBody LedgerMasterRequestDto request
    ) {
        LedgerMasterResponseDto response = ledgerMasterService.createLedger(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ledger")
    public ResponseEntity<LedgerMasterResponseDto> updateLedger(
            @PathVariable Long id,
            @RequestParam Long userId,
            @Valid @RequestBody LedgerMasterRequestDto request
    ) {
        LedgerMasterResponseDto response = ledgerMasterService.updateLedger(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ledger by ID")
    public ResponseEntity<LedgerMasterResponseDto> getLedgerById(
            @PathVariable Long id
    ) {
        LedgerMasterResponseDto response = ledgerMasterService.getLedgerById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get ledger transactions / ledger statement by ledger ID")
    public ResponseEntity<LedgerStatementResponseDto> getLedgerTransactions(
            @PathVariable Long id,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false) String search,
            @RequestParam(required = false) String voucherType,
            @RequestParam(required = false) String sourceType,

            /*
             * entryType values:
             * DEBIT
             * CREDIT
             */
            @RequestParam(required = false) String entryType,

            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LedgerStatementResponseDto response =
                ledgerMasterService.getLedgerTransactions(
                        id,
                        fromDate,
                        toDate,
                        search,
                        voucherType,
                        sourceType,
                        entryType,
                        page - 1,
                        size
                );

        return ResponseEntity.ok(response);
    }

    // Demanded by Frontend Developer not from backend
    @GetMapping
    @Operation(summary = "Get paginated ledgers")
    public ResponseEntity<Page<LedgerMasterResponseDto>> getLedgers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LedgerType ledgerType,
            @RequestParam(required = false) Long ledgerGroupId,
            @RequestParam(required = false) LedgerGroupType ledgerGroupType,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<LedgerMasterResponseDto> response = ledgerMasterService.getLedgers(
                search,
                ledgerType,
                ledgerGroupId,
                ledgerGroupType,
                companyId,
                unitId,
                active,
                page - 1,
                size
        );

        return ResponseEntity.ok(response);
    }


    @GetMapping("/active")
    @Operation(summary = "Get active BANK, CASH and PAYMENT_GATEWAY ledgers")
    public ResponseEntity<List<LedgerMasterResponseDto>> getReceiptLedgers() {
        List<LedgerMasterResponseDto> response = ledgerMasterService.getReceiptLedgers();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete ledger")
    public ResponseEntity<Void> deleteLedger(
            @PathVariable Long id
    ) {
        ledgerMasterService.deleteLedger(id);
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/debit-credit-types")
    @Operation(summary = "Get debit/credit types")
    public ResponseEntity<List<Map<String, String>>> getDebitCreditTypes() {

        List<Map<String, String>> response = Arrays.stream(DebitCredit.values())
                .map(type -> Map.of(
                        "value", type.name(),
                        "label", formatDebitCreditLabel(type)
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    private String formatDebitCreditLabel(DebitCredit type) {
        String value = type.name().toLowerCase();

        return value.substring(0, 1).toUpperCase()
                + value.substring(1);
    }


}