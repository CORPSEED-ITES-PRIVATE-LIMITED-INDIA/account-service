package com.account.controller.estimate;

import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.estimate.EstimateResponseDto;
import com.account.service.EstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/all")
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
            @RequestParam(value = "size", defaultValue = "20") int size) {

        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 200) {
            size = 20;
        }

        List<EstimateResponseDto> estimates = estimateService.getAllEstimates(
                requestingUserId,
                page - 1,   // convert to 0-based for Spring Data
                size
        );

        return ResponseEntity.ok(estimates);
    }

    @GetMapping("/count")
    @Operation(summary = "Get total count of estimates",
            description = "ADMIN: total estimates in system | Regular user: only estimates created by the user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Count returned"),
            @ApiResponse(responseCode = "400", description = "Invalid user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Long> getEstimatesCount(
            @RequestParam("userId") Long requestingUserId) {

        long count = estimateService.getEstimatesCount(requestingUserId);
        return ResponseEntity.ok(count);
    }


}