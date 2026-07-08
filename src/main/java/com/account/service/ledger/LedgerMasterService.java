package com.account.service.ledger;

import com.account.domain.ledger.LedgerGroupType;
import com.account.domain.ledger.LedgerType;
import com.account.dto.ledger.LedgerMasterRequestDto;
import com.account.dto.ledger.LedgerMasterResponseDto;
import com.account.dto.ledger.LedgerStatementResponseDto;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface LedgerMasterService {

    LedgerMasterResponseDto createLedger(LedgerMasterRequestDto request);

    LedgerMasterResponseDto updateLedger(Long id, LedgerMasterRequestDto request);

    LedgerMasterResponseDto getLedgerById(Long id);

    List<LedgerMasterResponseDto> getActiveLedgers();

    void deleteLedger(Long id);

    LedgerStatementResponseDto getLedgerTransactions(
            Long id,
            LocalDate fromDate,
            LocalDate toDate,
            String search,
            String voucherType,
            String sourceType,
            String entryType,
            int page,
            int size
    );

    Page<LedgerMasterResponseDto> getLedgers(String search, LedgerType ledgerType, Long ledgerGroupId,
                                             LedgerGroupType ledgerGroupType, Long companyId, Long unitId, Boolean active, int i, int size);


    List<LedgerMasterResponseDto> getReceiptLedgers();


}