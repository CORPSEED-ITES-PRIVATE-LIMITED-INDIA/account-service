package com.account.service.ledger;


import com.account.dto.GovernmentFeeFundTransferPostingRequestDto;
import com.account.dto.GovernmentFeeFundTransferPostingResponseDto;
import com.account.dto.operationService.GovernmentFeePostingRequestDto;
import com.account.dto.operationService.GovernmentFeePostingResponseDto;

public interface ProjectExpenseAccountingService {

    GovernmentFeePostingResponseDto postGovernmentFeeExpense(
            GovernmentFeePostingRequestDto request
    );

    GovernmentFeeFundTransferPostingResponseDto postGovernmentFeeFundTransfer(GovernmentFeeFundTransferPostingRequestDto request);


}