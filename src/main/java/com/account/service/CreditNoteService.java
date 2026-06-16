package com.account.service;

import com.account.domain.creditNote.CreditNoteStatus;
import com.account.dto.creditNote.ApproveCreditNoteRequestDto;
import com.account.dto.creditNote.CreateCreditNoteRefundRequestDto;
import com.account.dto.creditNote.CreditNoteResponseDto;
import com.account.dto.creditNote.RejectCreditNoteRequestDto;
import org.springframework.data.domain.Page;

public interface CreditNoteService {

    CreditNoteResponseDto createRefundCreditNote(CreateCreditNoteRefundRequestDto request);

    CreditNoteResponseDto approveCreditNoteByAccount(
            Long creditNoteId,
            Long userId,
            ApproveCreditNoteRequestDto request
    );

    CreditNoteResponseDto approveCreditNote(
            Long creditNoteId,
            Long userId,
            ApproveCreditNoteRequestDto request
    );

    CreditNoteResponseDto rejectCreditNote(
            Long creditNoteId,
            Long userId,
            RejectCreditNoteRequestDto request
    );

    Page<CreditNoteResponseDto> getCreditNotes(
            CreditNoteStatus status,
            Long unbilledId,
            int page,
            int size
    );
}