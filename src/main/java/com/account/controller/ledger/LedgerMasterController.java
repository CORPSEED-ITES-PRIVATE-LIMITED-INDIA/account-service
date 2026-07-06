package com.account.controller.ledger;

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

import java.time.LocalDate;

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
            @Valid @RequestBody LedgerMasterRequestDto request
    ) {
        LedgerMasterResponseDto response = ledgerMasterService.updateLedger(id, request);
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

            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LedgerStatementResponseDto response =
                ledgerMasterService.getLedgerTransactions(
                        id,
                        fromDate,
                        toDate,
                        page - 1,
                        size
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get paginated ledgers")
    public ResponseEntity<Page<LedgerMasterResponseDto>> getLedgers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LedgerType ledgerType,
            @RequestParam(required = false) Long ledgerGroupId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size

    ) {
        Page<LedgerMasterResponseDto> response = ledgerMasterService.getLedgers(
                search,
                ledgerType,
                ledgerGroupId,
                active,
                page - 1,
                size
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active ledgers")
    public ResponseEntity<?> getActiveLedgers() {
        return ResponseEntity.ok(ledgerMasterService.getActiveLedgers());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete ledger")
    public ResponseEntity<Void> deleteLedger(
            @PathVariable Long id
    ) {
        ledgerMasterService.deleteLedger(id);
        return ResponseEntity.noContent().build();
    }
}