package com.account.controller;

import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.company.CompanyRequestDto;
import com.account.dto.company.CompanyResponseDto;
import com.account.service.company.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accountService/api/v1")
public class CompanyController {

    @Autowired
    private CompanyService companyService;

    @Operation(summary = "Create company in account service from lead service data")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Company created successfully in account service"),
            @ApiResponse(responseCode = "400", description = "Invalid request data"),
            @ApiResponse(responseCode = "409", description = "Company with PAN or ID conflict"),
            @ApiResponse(responseCode = "404", description = "Referenced entity not found")
    })
    @PostMapping("/createCompany")
    public ResponseEntity<CompanyResponseDto> createCompany(
            @Valid @RequestBody CompanyRequestDto requestDto) {

        CompanyResponseDto response = companyService.createCompanyFromLead(requestDto);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

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

}