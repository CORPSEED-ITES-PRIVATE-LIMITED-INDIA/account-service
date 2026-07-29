package com.account.controller.ledger;

import com.account.dto.operationService.GovernmentFeePostingRequestDto;
import com.account.dto.operationService.GovernmentFeePostingResponseDto;
import com.account.service.ledger.ProjectExpenseAccountingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

        GovernmentFeePostingResponseDto response =
                projectExpenseAccountingService
                        .postGovernmentFeeExpense(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}