package com.account.controller.ledger;

import com.account.dto.operationService.*;
import com.account.dto.operationService.GovernmentFeeFundTransferPostingRequestDto;
import com.account.dto.operationService.GovernmentFeeFundTransferPostingResponseDto;
import com.account.dto.operationService.GovernmentFeePaymentPostingRequestDto;
import com.account.dto.operationService.GovernmentFeePaymentPostingResponseDto;
import com.account.service.ledger.ProjectExpenseAccountingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping("/accountService/api/v1/internal/project-expenses")
@RequiredArgsConstructor
public class ProjectExpenseAccountingController {

    private final ProjectExpenseAccountingService projectExpenseAccountingService;

    @PostMapping("/government-fee/post")
    public ResponseEntity<GovernmentFeePostingResponseDto> postGovernmentFee(
            @Valid @RequestBody GovernmentFeePostingRequestDto request
    ) {

        log.info(
                "[GOVERNMENT-FEE-POSTING-REQUEST] operationExpenseId={} | projectId={} | paidBy={} | approvedAmount={} | bankLedgerId={}",
                request.getOperationExpenseId(),
                request.getProjectId(),
                request.getPaidBy(),
                request.getApprovedAmount(),
                request.getClientPaymentBankLedgerId()
        );

        GovernmentFeePostingResponseDto response =
                projectExpenseAccountingService.postGovernmentFeeExpense(request);

        HttpStatus status = "POSTED".equalsIgnoreCase(response.getPostingStatus())
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/government-fee/fund-transfer")
    public ResponseEntity<GovernmentFeeFundTransferPostingResponseDto>
    postGovernmentFeeFundTransfer(
            @Valid @RequestBody
            GovernmentFeeFundTransferPostingRequestDto request
    ) {
        GovernmentFeeFundTransferPostingResponseDto response =
                projectExpenseAccountingService
                        .postGovernmentFeeFundTransfer(request);

        HttpStatus status = "POSTED".equalsIgnoreCase(
                response.getPostingStatus())
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/government-fee/payment")
    public ResponseEntity<GovernmentFeePaymentPostingResponseDto>
    postGovernmentFeePayment(
            @Valid @RequestBody GovernmentFeePaymentPostingRequestDto request
    ) {
        log.info(
                "[GOVERNMENT-FEE-PAYMENT-REQUEST] operationExpenseId={} | projectId={} | bankLedgerId={} | amount={} | paymentDate={} | reference={}",
                request.getOperationExpenseId(),
                request.getProjectId(),
                request.getPaymentBankLedgerId(),
                request.getAmount(),
                request.getPaymentDate(),
                request.getPaymentReference()
        );

        GovernmentFeePaymentPostingResponseDto response =
                projectExpenseAccountingService
                        .postGovernmentFeePayment(request);

        HttpStatus status = "POSTED".equalsIgnoreCase(
                response.getPostingStatus())
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }


    @GetMapping("/government-fee")
    public ResponseEntity<Page<GovernmentExpenseListItemDto>>
    getGovernmentFeeExpenses(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Direction.DESC,
                                "id"
                        )
                );

        Page<GovernmentExpenseListItemDto> response =
                projectExpenseAccountingService
                        .getGovernmentFeeExpenses(pageable);

        return ResponseEntity.ok(response);
    }

}
