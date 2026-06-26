package com.account.controller.ledger;

import com.account.domain.ledger.LedgerGroupType;
import com.account.dto.ledger.LedgerGroupRequestDto;
import com.account.dto.ledger.LedgerGroupResponseDto;
import com.account.service.ledger.LedgerGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accountService/api/v1/ledger-groups")
@RequiredArgsConstructor
@Tag(name = "Ledger Groups", description = "APIs for ledger group master")
public class LedgerGroupController {

    private final LedgerGroupService ledgerGroupService;

    @PostMapping
    @Operation(summary = "Create ledger group")
    public ResponseEntity<LedgerGroupResponseDto> createLedgerGroup(
            @Valid @RequestBody LedgerGroupRequestDto request
    ) {
        LedgerGroupResponseDto response = ledgerGroupService.createLedgerGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ledger group")
    public ResponseEntity<LedgerGroupResponseDto> updateLedgerGroup(
            @PathVariable Long id,
            @Valid @RequestBody LedgerGroupRequestDto request
    ) {
        LedgerGroupResponseDto response = ledgerGroupService.updateLedgerGroup(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ledger group by ID")
    public ResponseEntity<LedgerGroupResponseDto> getLedgerGroupById(
            @PathVariable Long id
    ) {
        LedgerGroupResponseDto response = ledgerGroupService.getLedgerGroupById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get paginated ledger groups")
    public ResponseEntity<Page<LedgerGroupResponseDto>> getLedgerGroups(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) LedgerGroupType groupType,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<LedgerGroupResponseDto> response = ledgerGroupService.getLedgerGroups(
                search,
                groupType,
                active,
                page - 1,
                size
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "Get active ledger groups")
    public ResponseEntity<List<LedgerGroupResponseDto>> getActiveLedgerGroups() {
        List<LedgerGroupResponseDto> response = ledgerGroupService.getActiveLedgerGroups();
        return ResponseEntity.ok(response);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete ledger group")
    public ResponseEntity<Void> deleteLedgerGroup(
            @PathVariable Long id
    ) {
        ledgerGroupService.deleteLedgerGroup(id);
        return ResponseEntity.noContent().build();
    }
}