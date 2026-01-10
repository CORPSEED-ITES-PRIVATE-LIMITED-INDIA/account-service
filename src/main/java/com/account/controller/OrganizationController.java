package com.account.controller;

import com.account.dto.OrganizationRequestDto;
import com.account.dto.OrganizationResponseDto;
import com.account.service.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@Tag(name = "Organization Configuration", description = "Admin APIs to manage Corpseed organization details")
@RestController
@RequestMapping("/accountService/api/v1")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @Operation(summary = "Create or update organization configuration (ADMIN only)")
    @ApiResponse(responseCode = "200", description = "Organization updated successfully")
    @PostMapping("/createOrganization")
    public ResponseEntity<OrganizationResponseDto> createOrganization(
            @Valid @RequestBody OrganizationRequestDto requestDto , Long userId) {


        OrganizationResponseDto updated = organizationService.saveOrganization(requestDto, userId);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Get current organization configuration")
    @ApiResponse(responseCode = "200", description = "Current organization details")
    @GetMapping
    public ResponseEntity<OrganizationResponseDto> getOrganization() {
        OrganizationResponseDto dto = organizationService.getOrganization();
        return ResponseEntity.ok(dto);
    }
}