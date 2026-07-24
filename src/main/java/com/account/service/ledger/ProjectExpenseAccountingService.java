package com.account.service.ledger;


import com.account.dto.operationService.GovernmentFeePostingRequestDto;
import com.account.dto.operationService.GovernmentFeePostingResponseDto;

public interface ProjectExpenseAccountingService {

    GovernmentFeePostingResponseDto postGovernmentFeeExpense(
            GovernmentFeePostingRequestDto request
    );
}