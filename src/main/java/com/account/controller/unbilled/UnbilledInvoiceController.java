package com.account.controller.unbilled;

import com.account.domain.UnbilledStatus;
import com.account.dto.estimate.EstimateResponseDto;
import com.account.dto.operationService.OperationProjectActivityResponseDto;
import com.account.dto.payment.GovernmentFeeResponseDto;
import com.account.dto.payment.TdsResponseDto;
import com.account.dto.unbilled.*;
import com.account.exception.ResourceNotFoundException;
import com.account.service.PaymentService;
import com.account.service.UnbilledService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Unbilled Invoices", description = "Operations related to unbilled / proforma / advance invoices (approval flow for accounts team)")
@RestController
@RequestMapping("/accountService/api/v1/unbilled-invoices")
@RequiredArgsConstructor
@Validated
public class UnbilledInvoiceController {

    private final PaymentService paymentService;

    private final UnbilledService unbilledService;

    // ────────────────────────────────────────────────
    //  Approve unbilled invoice (usually done by Accounts)
    // ────────────────────────────────────────────────
    @Operation(
            summary = "Approve unbilled invoice",
            description = "Approves the unbilled invoice, changes status to APPROVED, " +
                    "and triggers generation of the final tax invoice (GST invoice)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully approved and invoice generated"),
            @ApiResponse(responseCode = "400", description = "Invalid request or validation error", content = @Content),
            @ApiResponse(responseCode = "404", description = "Unbilled invoice not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Invoice already approved / wrong status", content = @Content)
    })
    @PostMapping("/{unbilledId}/updateStatus")
    public ResponseEntity<UnbilledInvoiceApprovalResponseDto> updateUnbilledInvoiceStatus(
            @PathVariable @Parameter(description = "ID of the unbilled invoice") Long unbilledId,
            @Valid @RequestBody UnbilledInvoiceApprovalRequestDto request) {

        UnbilledInvoiceApprovalResponseDto response =
                paymentService.updateUnbilledInvoiceStatus(unbilledId, request);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get list of unbilled invoices (paginated)",
            description = "Returns a paginated list of unbilled invoices. Page numbering starts from 1. Default sorting: createdAt DESC."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or filter parameters", content = @Content)
    })
    @GetMapping("/list")
    public ResponseEntity<List<UnbilledInvoiceSummaryDto>> getUnbilledInvoicesList(
            @RequestParam(value = "status", required = false)
            @Parameter(description = "Filter by unbilled invoice status") UnbilledStatus status,

            @RequestParam(value = "userId", required = false)
            @Parameter(description = "Filter by user who created or approved the record") Long userId,

            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        if (page < 1 || size < 1) {
            throw new IllegalArgumentException("Page and size must be greater than 0");
        }

        List<UnbilledInvoiceSummaryDto> list =
                unbilledService.getUnbilledInvoicesList(userId, status, page - 1, size);

        return ResponseEntity.ok(list);
    }

    @Operation(
            summary = "Get count of unbilled invoices",
            description = "Returns only the total number of unbilled invoices matching the optional filters (status and/or userId)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters", content = @Content)
    })
    @GetMapping("/count")
    public ResponseEntity<Long> getUnbilledInvoicesCount(
            @RequestParam(value = "status", required = false)
            @Parameter(description = "Filter by unbilled invoice status") UnbilledStatus status,

            @RequestParam(value = "userId", required = false)
            @Parameter(description = "Filter by user who created or approved the record") Long userId
    ) {
        long count = paymentService.getUnbilledInvoicesCount(userId, status);
        return ResponseEntity.ok(count);
    }


    @Operation(
            summary = "Search unbilled invoices by unbilled number and/or company name (paginated)",
            description = "Returns a paginated list of unbilled invoices matching the search criteria (partial match, case-insensitive)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search results returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination or search parameters", content = @Content)
    })

    @PostMapping("/search")
    public ResponseEntity<List<UnbilledInvoiceSummaryDto>> searchUnbilledInvoices(
            @RequestBody UnbilledInvoiceSearchRequest request
    ) {
        int page = request.getPage();
        int size = request.getSize();

        // Normalize page: frontend (1-based) → backend (0-based)
        int normalizedPage = page - 1;

        List<UnbilledInvoiceSummaryDto> list =
                paymentService.searchUnbilledInvoices(
                        request.getUnbilledNumber(),
                        request.getCompanyName(),
                        request.getEstimateNumber(),
                        normalizedPage,
                        size
                );

        return ResponseEntity.ok(list);
    }

    @Operation(
            summary = "Get count of unbilled invoices matching search criteria",
            description = "Returns the total number of unbilled invoices matching the optional search filters (unbilledNumber and/or companyName)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search parameters", content = @Content)
    })
    @GetMapping("/search/count")
    public ResponseEntity<Long> countSearchUnbilledInvoices(
            @RequestParam(value = "unbilledNumber", required = false)
            @Parameter(description = "Partial unbilled number to search for") String unbilledNumber,

            @RequestParam(value = "companyName", required = false)
            @Parameter(description = "Partial company name to search for") String companyName
    ) {
        long count = paymentService.countSearchUnbilledInvoices(unbilledNumber, companyName);
        return ResponseEntity.ok(count);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full detailed invoice by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoice details"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<UnbilledInvoiceDetailDto> getInvoiceDetail(
            @PathVariable Long id,
            @RequestParam Long userId
    ) {
        UnbilledInvoiceDetailDto unbilledInvoiceDetailDto = paymentService.getUnbilledInvoice(id, userId);
        return ResponseEntity.ok(unbilledInvoiceDetailDto);
    }



    @PutMapping("/cancel/{userId}/{id}")
    public ResponseEntity<?> cancelUnbilled(
            @PathVariable Long userId,
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) String cancelAttachment
            ) {

        paymentService.cancelUnbilled(userId, id, reason, cancelAttachment);

        return ResponseEntity.ok("Unbilled cancelled successfully");
    }

    @GetMapping("/getExpences/{userId}/{unbilledId}")
    public ResponseEntity<Page<OperationProjectActivityResponseDto>> getExpences(
            @PathVariable Long userId,
            @PathVariable Long unbilledId,
            Pageable pageable
    ){
        Page<OperationProjectActivityResponseDto> response  = paymentService.getExpences(userId, unbilledId, pageable);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    @PutMapping("/approveExpense/{userId}/{unbilledId}/{expenseId}")
    public ResponseEntity<?> approveExpense(
            @PathVariable Long userId,
            @PathVariable Long unbilledId,
            @PathVariable Long expenseId,
            @RequestParam String status
    ) {

        paymentService.approveExpense(userId, unbilledId, expenseId, status);

        return new ResponseEntity<>("Expense approved successfully", HttpStatus.OK);
    }


    @PostMapping("/convertIntoADI/{unbilledId}")
    public ResponseEntity<UnbilledInvoiceDetailDto> convertIntoADI(
            @PathVariable Long unbilledId,
            @RequestParam Long requestingUserId
    ){
        UnbilledInvoiceDetailDto dto = paymentService.convertIntoADI(unbilledId, requestingUserId);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/government-fee")
    public ResponseEntity<?> getGovernmentFee(
            @RequestParam(required = false) Long unbilledId,
            @RequestParam(required = false) Long estimateId) {
        try {
            GovernmentFeeResponseDto response = paymentService.getGovernmentFee(unbilledId, estimateId);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (ValidationException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (ResourceNotFoundException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/tds")
    public ResponseEntity<TdsResponseDto> getTds(
            @RequestParam(required = false) Long unbilledId,
            @RequestParam(required = false) Long estimateId) {

        TdsResponseDto response = paymentService.getTds(unbilledId, estimateId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/number/{unbilledNumber}")
    @Operation(summary = "Get unbilled invoice by Unbilled Number",
            description = "Fetches full details of unbilled invoice using its unique number (e.g. UNB-2026-00001234)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unbilled invoice found"),
            @ApiResponse(responseCode = "404", description = "Unbilled invoice not found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<UnbilledInvoiceDetailDto> getUnbilledInvoiceByNumber(
            @PathVariable String unbilledNumber,
            @RequestParam("userId") Long userId) {

        UnbilledInvoiceDetailDto dto = paymentService.getUnbilledInvoiceByNumber(unbilledNumber, userId);
        return ResponseEntity.ok(dto);
    }

    @Operation(
            summary = "Get unbilled invoice report",
            description = "Returns unbilled invoices filtered by userId, createdByUserId, status, fromDate and toDate."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid report filter parameters", content = @Content)
    })
    @GetMapping("/report")
    public ResponseEntity<List<UnbilledInvoiceSummaryDto>> getUnbilledReport(
            @RequestParam(value = "userId", required = false)
            @Parameter(description = "Logged-in/requesting user ID. Example: Praveen account user ID") Long userId,

            @RequestParam(value = "createdByUserId", required = false)
            @Parameter(description = "Filter by user who created the unbilled invoice. Example: Dhruv/Rahul/Rajeev user ID") Long createdByUserId,

            @RequestParam(value = "status", required = false)
            @Parameter(description = "Filter by unbilled invoice status") UnbilledStatus status,

            @RequestParam(value = "fromDate", required = false)
            @Parameter(description = "Report start date in yyyy-MM-dd format") String fromDate,

            @RequestParam(value = "toDate", required = false)
            @Parameter(description = "Report end date in yyyy-MM-dd format") String toDate
    ) {
        LocalDate parsedFromDate = parseDate(fromDate, "fromDate");
        LocalDate parsedToDate = parseDate(toDate, "toDate");

        List<UnbilledInvoiceSummaryDto> response =
                unbilledService.getUnbilledReport(
                        userId,
                        createdByUserId,
                        status,
                        parsedFromDate,
                        parsedToDate
                );

        return ResponseEntity.ok(response);
    }
    @Operation(
            summary = "Get unbilled invoice report count",
            description = "Returns total count of unbilled invoices matching userId, createdByUserId, status, fromDate and toDate filters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Report count returned successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid report filter parameters", content = @Content)
    })
    @GetMapping("/report/count")
    public ResponseEntity<Long> getUnbilledReportCount(
            @RequestParam(value = "userId", required = false)
            @Parameter(description = "Logged-in/requesting user ID. Example: Praveen account user ID") Long userId,

            @RequestParam(value = "createdByUserId", required = false)
            @Parameter(description = "Filter by user who created the unbilled invoice. Example: Dhruv/Rahul/Rajeev user ID") Long createdByUserId,

            @RequestParam(value = "status", required = false)
            @Parameter(description = "Filter by unbilled invoice status") UnbilledStatus status,

            @RequestParam(value = "fromDate", required = false)
            @Parameter(description = "Report start date in yyyy-MM-dd format") String fromDate,

            @RequestParam(value = "toDate", required = false)
            @Parameter(description = "Report end date in yyyy-MM-dd format") String toDate
    ) {
        LocalDate parsedFromDate = parseDate(fromDate, "fromDate");
        LocalDate parsedToDate = parseDate(toDate, "toDate");

        long count = unbilledService.getUnbilledReportCount(
                userId,
                createdByUserId,
                status,
                parsedFromDate,
                parsedToDate
        );

        return ResponseEntity.ok(count);
    }


    private LocalDate parseDate(String date, String fieldName) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(date.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    fieldName + " must be in yyyy-MM-dd format. Invalid value: " + date
            );
        }
    }

}