package com.account.controller;

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






}