package com.account.controller.estimate;

import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.dashboard.EstimateDashboardFilterRequest;
import com.account.dto.dashboard.EstimateDashboardResponse;
import com.account.dto.estimate.EstimateFilterRequest;
import com.account.dto.estimate.EstimateResponseDto;
import com.account.dto.estimate.EstimateSearchRequest;
import com.account.dto.estimate.EstimateSearchRequestDto;
import com.account.service.EstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Estimate Management", description = "APIs for creating, viewing and listing estimates")
@RestController
@RequestMapping("/accountService/api/v1/estimates")
@RequiredArgsConstructor
public class EstimateController {

    private final EstimateService estimateService;

    @Operation(summary = "Create a new estimate")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estimate created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "Referenced entities not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping
    public ResponseEntity<EstimateResponseDto> createEstimate(
            @Valid @RequestBody EstimateCreationRequestDto requestDto) {
        EstimateResponseDto response = estimateService.createEstimate(requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{estimateId}")
    @Operation(summary = "Get single estimate by ID",
            description = "Fetches estimate details. Access is validated based on the requesting user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estimate found and returned"),
            @ApiResponse(responseCode = "403", description = "User does not have permission to view this estimate"),
            @ApiResponse(responseCode = "404", description = "Estimate not found"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<EstimateResponseDto> getEstimate(
            @PathVariable("estimateId") Long estimateId,

            @RequestParam("userId")
            Long userId) {

        EstimateResponseDto response = estimateService.getEstimateById(estimateId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/lead/{leadId}")
    @Operation(summary = "Get all estimates for a given lead ID",
            description = "Returns list of estimates created against a specific lead. " +
                    "Typically used in CRM/lead management screens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of estimates returned (can be empty)"),
            @ApiResponse(responseCode = "400", description = "Invalid lead ID"),
            @ApiResponse(responseCode = "403", description = "User does not have permission"),
            @ApiResponse(responseCode = "404", description = "No estimates found (optional - can return 200 with empty list)")
    })
    public ResponseEntity<List<EstimateResponseDto>> getEstimatesByLeadId(
            @PathVariable Long leadId,
            @RequestParam(value = "userId", required = false) Long requestingUserId) {

        List<EstimateResponseDto> estimates = estimateService.getEstimatesByLeadId(leadId);

        return ResponseEntity.ok(estimates);
    }


    @GetMapping("/company/{companyId}")
    @Operation(summary = "Get all estimates for a given company ID",
            description = "Returns list of all estimates created for a specific company. " +
                    "Useful in account/company dashboard views.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of estimates (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Invalid company ID"),
            @ApiResponse(responseCode = "404", description = "Company not found (optional)")
    })
    public ResponseEntity<List<EstimateResponseDto>> getEstimatesByCompanyId(
            @PathVariable Long companyId,
            @RequestParam(value = "userId", required = false) Long requestingUserId) {

        List<EstimateResponseDto> estimates = estimateService.getEstimatesByCompanyId(companyId);
        return ResponseEntity.ok(estimates);
    }

    @PostMapping("/all")
    @Operation(
            summary = "Get all estimates (paginated)",
            description = "ADMIN: sees every estimate in the system. Normal user: sees only estimates they created."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of estimates"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<List<EstimateResponseDto>> getAllEstimates(
            @RequestParam("userId") Long requestingUserId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestBody(required = false) EstimateFilterRequest filter) {

        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }

        // Null-safe handling
        String search = filter != null ? filter.getSearch() : null;
        String status = filter != null ? filter.getStatus() : null;
        LocalDate fromDate = filter != null ? filter.getFromDate() : null;
        LocalDate toDate = filter != null ? filter.getToDate() : null;

        List<EstimateResponseDto> estimates = estimateService.getAllEstimates(
                requestingUserId,
                search,
                status,
                fromDate,
                toDate,
                page - 1,
                size
        );

        return ResponseEntity.ok(estimates);
    }




    @PostMapping("/count")
    @Operation(
            summary = "Get total count of estimates (with filters)",
            description = "ADMIN: counts all estimates | Regular user: counts only own estimates | Applies search/status/date filters"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count returned"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Long> getEstimatesCount(
            @RequestParam("userId") Long requestingUserId,
            @RequestBody(required = false) EstimateFilterRequest filter) {

        String search = filter != null ? filter.getSearch() : null;
        String status = filter != null ? filter.getStatus() : null;
        LocalDate fromDate = filter != null ? filter.getFromDate() : null;
        LocalDate toDate = filter != null ? filter.getToDate() : null;

        long count = estimateService.getEstimatesCount(
                requestingUserId,
                search,
                status,
                fromDate,
                toDate
        );

        return ResponseEntity.ok(count);
    }


    @PostMapping("/{estimateId}/send")
    @Operation(summary = "Send estimate to client",
            description = "Sends estimate to the primary email of the associated contact. No body required.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estimate sent successfully – status updated"),
            @ApiResponse(responseCode = "400", description = "Cannot send (wrong status, no email, etc.)"),
            @ApiResponse(responseCode = "403", description = "No permission"),
            @ApiResponse(responseCode = "404", description = "Estimate or contact not found")
    })
    public ResponseEntity<EstimateResponseDto> sendEstimateToClient(
            @PathVariable Long estimateId,
            @RequestParam("userId") Long requestingUserId) {

        EstimateResponseDto response = estimateService.sendEstimateToClient(
                estimateId,
                requestingUserId
        );

        return ResponseEntity.ok(response);
    }



    @PostMapping("/estimatesAnalytics")
    public ResponseEntity<EstimateDashboardResponse> getDashboard(
            @RequestBody EstimateDashboardFilterRequest request
    ) {

        EstimateDashboardResponse response =
                estimateService.getEstimateDashboard(request);

        return ResponseEntity.ok(response);
    }


    @PostMapping("/estimateReport")
    public ResponseEntity<Page<EstimateResponseDto>> estimateReport(
            @RequestBody EstimateSearchRequest request
    ) {

        Page<EstimateResponseDto> result =
                estimateService.estimateReport(request);

        return ResponseEntity.ok(result);
    }


    @PostMapping("/search/{userId}")
    public ResponseEntity<Page<EstimateResponseDto>> searchEstimates(
            @PathVariable Long userId,
            @RequestBody EstimateSearchRequestDto request
    ) {

        Page<EstimateResponseDto> result =
                estimateService.searchEstimates(request, userId);

        return ResponseEntity.ok(result);
    }





}