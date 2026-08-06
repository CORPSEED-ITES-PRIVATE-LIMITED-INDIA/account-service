package com.account.service.ledger;

import com.account.dto.operationService.*;
import com.account.dto.operationService.GovernmentFeeFundTransferPostingRequestDto;
import com.account.dto.operationService.GovernmentFeeFundTransferPostingResponseDto;
import com.account.dto.operationService.GovernmentFeePaymentPostingRequestDto;
import com.account.dto.operationService.GovernmentFeePaymentPostingResponseDto;

public interface ProjectExpenseAccountingService {

    GovernmentFeePostingResponseDto postGovernmentFeeExpense(
            GovernmentFeePostingRequestDto request
    );

    GovernmentFeeFundTransferPostingResponseDto
    postGovernmentFeeFundTransfer(
            GovernmentFeeFundTransferPostingRequestDto request
    );

    GovernmentFeePaymentPostingResponseDto postGovernmentFeePayment(
            GovernmentFeePaymentPostingRequestDto request
    );
}
