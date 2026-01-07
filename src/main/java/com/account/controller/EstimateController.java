package com.account.controller;

import com.account.dto.estimate.EstimateRequestDto;
import com.account.dto.estimate.EstimateResponseDto;
import com.account.service.EstimateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/estimates")
public class EstimateController {

    @Autowired
    private EstimateService estimateService;

    // ==================== CREATE ESTIMATE ====================

    @Operation(summary = "Create a new estimate with full details")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Estimate created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "404", description = "Company, Unit or Contact not found"),
            @ApiResponse(responseCode = "409", description = "Order number already exists")
    })
    @PostMapping
    public ResponseEntity<EstimateResponseDto> createEstimate(
            @Valid @RequestBody EstimateRequestDto requestDto) {

        EstimateResponseDto response = estimateService.createEstimate(requestDto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // ==================== GET ESTIMATES ====================

    @Operation(summary = "Get estimate by ID")
    @GetMapping("/{id}")
    public ResponseEntity<EstimateResponseDto> getEstimateById(@PathVariable Long id) {
        EstimateResponseDto estimate = estimateService.getEstimateById(id);
        if (estimate == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(estimate);
    }

    @Operation(summary = "Get all estimates for a specific company")
    @GetMapping("/company/{companyId}")
    public ResponseEntity<List<EstimateResponseDto>> getEstimatesByCompany(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<EstimateResponseDto> estimates = estimateService.getEstimatesByCompanyId(companyId, page, size);
        return ResponseEntity.ok(estimates);
    }

    @Operation(summary = "Get all estimates (with pagination)")
    @GetMapping
    public ResponseEntity<List<EstimateResponseDto>> getAllEstimates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<EstimateResponseDto> estimates = estimateService.getAllEstimates(page, size);
        return ResponseEntity.ok(estimates);
    }

    @Operation(summary = "Search estimates by product name, company name, order number, etc.")
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchEstimates(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.ok(estimateService.searchEstimates(query, page, size));
    }

    @Operation(summary = "Get detailed estimate view (rich format for frontend/PDF)")
    @GetMapping("/view/{id}")
    public ResponseEntity<Map<String, Object>> getEstimateForView(@PathVariable Long id) {
        Map<String, Object> viewData = estimateService.getEstimateForView(id);
        return ResponseEntity.ok(viewData);
    }
}