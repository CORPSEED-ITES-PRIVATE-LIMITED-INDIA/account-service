package com.account.controller.unbilled;

import com.account.domain.creditNote.CreditNoteStatus;
import com.account.dto.creditNote.ApproveCreditNoteRequestDto;
import com.account.dto.creditNote.CreateCreditNoteRefundRequestDto;
import com.account.dto.creditNote.CreditNoteResponseDto;
import com.account.dto.creditNote.RejectCreditNoteRequestDto;
import com.account.service.CreditNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accountService/api/credit-notes")
@RequiredArgsConstructor
public class CreditNoteController {

    private final CreditNoteService creditNoteService;

    @PostMapping("/refund")
    public ResponseEntity<CreditNoteResponseDto> createRefundCreditNote(
            @RequestBody CreateCreditNoteRefundRequestDto request
    ) {

        CreditNoteResponseDto response =
                creditNoteService.createRefundCreditNote(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Step 2:
     * Account team reviews/approves the credit note.
     *
     * Status:
     * PENDING_ACCOUNT_REVIEW -> PENDING_ADMIN_APPROVAL
     */
    @PutMapping("/{creditNoteId}/account-approve/{userId}")
    public ResponseEntity<CreditNoteResponseDto> approveCreditNoteByAccount(
            @PathVariable Long creditNoteId,
            @PathVariable Long userId,
            @RequestBody(required = false) ApproveCreditNoteRequestDto request
    ) {

        CreditNoteResponseDto response =
                creditNoteService.approveCreditNoteByAccount(
                        creditNoteId,
                        userId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Step 3:
     * Admin gives final approval.
     *
     * Status:
     * PENDING_ADMIN_APPROVAL -> APPROVED
     *
     * Unbilled invoice cancellation should happen only after this step.
     */
    @PutMapping("/{creditNoteId}/approve/{userId}")
    public ResponseEntity<CreditNoteResponseDto> approveCreditNote(
            @PathVariable Long creditNoteId,
            @PathVariable Long userId,
            @RequestBody(required = false) ApproveCreditNoteRequestDto request
    ) {

        CreditNoteResponseDto response =
                creditNoteService.approveCreditNote(
                        creditNoteId,
                        userId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{creditNoteId}/reject/{userId}")
    public ResponseEntity<CreditNoteResponseDto> rejectCreditNote(
            @PathVariable Long creditNoteId,
            @PathVariable Long userId,
            @RequestBody RejectCreditNoteRequestDto request
    ) {

        CreditNoteResponseDto response =
                creditNoteService.rejectCreditNote(
                        creditNoteId,
                        userId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<CreditNoteResponseDto>> getCreditNotes(
            @RequestParam(required = false) CreditNoteStatus status,
            @RequestParam(required = false) Long unbilledId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {

        Page<CreditNoteResponseDto> response =
                creditNoteService.getCreditNotes(
                        status,
                        unbilledId,
                        page,
                        size
                );

        return ResponseEntity.ok(response);
    }
}