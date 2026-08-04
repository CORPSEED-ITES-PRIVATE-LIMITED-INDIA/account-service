package com.account.controller.ledger;

import com.account.dto.operationService.GovernmentFeePostingRequestDto;
import com.account.dto.operationService.GovernmentFeePostingResponseDto;
import com.account.service.ledger.ProjectExpenseAccountingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@RestController
@RequestMapping(
        "/accountService/api/v1/internal/project-expenses"
)
@RequiredArgsConstructor
public class ProjectExpenseAccountingController {

    private final ProjectExpenseAccountingService
            projectExpenseAccountingService;

    @PostMapping("/government-fee/post")
    public ResponseEntity<GovernmentFeePostingResponseDto>
    postGovernmentFee(
            @Valid
            @RequestBody
            GovernmentFeePostingRequestDto request
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
                projectExpenseAccountingService
                        .postGovernmentFeeExpense(request);

        /*
         * POSTED means vouchers were newly created.
         * ALREADY_POSTED and SKIPPED are idempotent responses.
         */
        HttpStatus responseStatus =
                "POSTED".equalsIgnoreCase(
                        response.getPostingStatus()
                )
                        ? HttpStatus.CREATED
                        : HttpStatus.OK;

        return ResponseEntity
                .status(responseStatus)
                .body(response);
    }
}