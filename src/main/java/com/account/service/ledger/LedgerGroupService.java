package com.account.service.ledger;

import com.account.domain.ledger.LedgerGroupType;
import com.account.dto.ledger.LedgerGroupRequestDto;
import com.account.dto.ledger.LedgerGroupResponseDto;
import org.springframework.data.domain.Page;

import java.util.List;

public interface LedgerGroupService {

    LedgerGroupResponseDto createLedgerGroup(LedgerGroupRequestDto request);

    LedgerGroupResponseDto updateLedgerGroup(Long id, LedgerGroupRequestDto request);

    LedgerGroupResponseDto getLedgerGroupById(Long id);

    Page<LedgerGroupResponseDto> getLedgerGroups(
            String search,
            LedgerGroupType groupType,
            Boolean active,
            int page,
            int size
    );

    List<LedgerGroupResponseDto> getActiveLedgerGroups();

    void deleteLedgerGroup(Long id);
}