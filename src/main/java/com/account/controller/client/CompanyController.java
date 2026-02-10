package com.account.controller.client;

import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.CompanyMigrationRequestDto;
import com.account.dto.company.request.ApproveRejectUnitRequestDto;
import com.account.dto.company.request.BasicUnitCreateRequest;
import com.account.dto.company.request.CompanyRequestDto;
import com.account.dto.company.response.CompanyResponseDto;
import com.account.service.company.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/accountService/api/v1")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Operation(summary = "Quick create company for urgent estimate (minimal details)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Quick company created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid minimal data"),
            @ApiResponse(responseCode = "409", description = "Company name already exists")
    })
    @PostMapping("/basic-company")
    public ResponseEntity<CompanyResponseDto> basicCreateCompany(
            @Valid @RequestBody BasicCompanyRequestDto quickRequest) {
        CompanyResponseDto response = companyService.basicCreateCompany(quickRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/migrateCompany")
    public ResponseEntity<CompanyResponseDto> migrateCompany(
            @Valid @RequestBody CompanyMigrationRequestDto dto
    ) {
        CompanyResponseDto response = companyService.migrateCompany(dto);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }


    @Operation(summary = "Quick add minimal unit/branch (sales urgent flow)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Basic unit added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid data or duplicate name"),
            @ApiResponse(responseCode = "404", description = "Company or user not found")
    })
    @PostMapping("/{companyId}/units/basic")
    public ResponseEntity<CompanyResponseDto> addBasicUnitToCompany(
            @PathVariable Long companyId,
            @RequestParam("updatedBy") Long updatedById,
            @Valid @RequestBody BasicUnitCreateRequest request) {
        if (updatedById == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "updatedBy user is required");
        }
        CompanyResponseDto response = companyService.addBasicUnitToCompany(companyId, request, updatedById);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Update company + units with complete details after payment",
            description = "Updates company profile and all units. " +
                    "Units are upserted by their ID (create if not exists, update if exists). " +
                    "Typically called after payment / onboarding step."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated successfully – usually moves to pending accounts review"),
            @ApiResponse(responseCode = "400", description = "Validation failed (duplicate name/PAN/GST, format error, missing required fields, etc.)"),
            @ApiResponse(responseCode = "404", description = "Company or updating user not found"),
            @ApiResponse(responseCode = "409", description = "Duplicate PAN, company name or unit name")
    })
    @PutMapping("/{companyId}/full-details")
    public ResponseEntity<CompanyResponseDto> updateFullCompanyDetails(
            @PathVariable Long companyId,
            @RequestParam(value = "updatedBy", required = true) Long updatedById,
            @Valid @RequestBody CompanyRequestDto dto) {
        if (updatedById == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "updatedBy user is required");
        }
        CompanyResponseDto response = companyService.updateFullCompanyDetails(companyId, dto, updatedById);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Fetch companies with pagination and filters")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Companies fetched successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @GetMapping("/companies")
    public ResponseEntity<List<CompanyResponseDto>> fetchCompanies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String onboardingStatus,
            @RequestParam(required = false) Long userId
    ) {
        List<CompanyResponseDto> response =
                companyService.fetchCompanies(page, size, onboardingStatus, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Approve or disapprove company onboarding")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Company reviewed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (missing approve flag, remark for disapproval, etc.)"),
            @ApiResponse(responseCode = "404", description = "Company or reviewer not found")
    })
    @PostMapping("/{companyId}/review")
    public ResponseEntity<CompanyResponseDto> reviewCompany(
            @PathVariable Long companyId,
            @RequestParam("reviewedBy") Long reviewedById,
            @Valid @RequestBody ApproveRejectUnitRequestDto request) {  // Reusing DTO, or create new if needed
        if (reviewedById == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewedBy user is required");
        }
        CompanyResponseDto response = companyService.reviewCompany(companyId, reviewedById, request);
        return ResponseEntity.ok(response);
    }


    @Operation(summary = "Approve or disapprove a specific company unit (branch/location)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unit reviewed successfully - company status may be updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request (missing approve flag, remark missing on rejection)"),
            @ApiResponse(responseCode = "404", description = "Company, unit or reviewer not found"),
            @ApiResponse(responseCode = "409", description = "Unit already approved/rejected in conflicting state (optional)")
    })
    @PostMapping("/companies/{companyId}/units/{unitId}/review")
    public ResponseEntity<CompanyResponseDto> reviewUnit(
            @PathVariable Long companyId,
            @PathVariable Long unitId,
            @RequestParam("reviewedBy") Long reviewedById,
            @Valid @RequestBody ApproveRejectUnitRequestDto request) {

        if (reviewedById == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewedBy user ID is required");
        }

        CompanyResponseDto response = companyService.reviewUnit(
                companyId,
                unitId,
                reviewedById,
                request
        );

        return ResponseEntity.ok(response);
    }

}