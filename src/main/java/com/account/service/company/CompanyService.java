package com.account.service.company;

import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.company.request.ApproveRejectUnitRequestDto;
import com.account.dto.company.request.BasicUnitCreateRequest;
import com.account.dto.company.request.CompanyRequestDto;
import com.account.dto.company.response.CompanyResponseDto;

import java.util.List;

public interface CompanyService {


    CompanyResponseDto basicCreateCompany(BasicCompanyRequestDto quickRequest);

    CompanyResponseDto addBasicUnitToCompany(Long companyId, BasicUnitCreateRequest request, Long updatedById);

    CompanyResponseDto updateFullCompanyDetails(Long companyId, CompanyRequestDto dto, Long updatedById);

    List<CompanyResponseDto> fetchCompanies(int page, int size, String onboardingStatus, Long userId);

    CompanyResponseDto reviewCompany(Long companyId, Long reviewedById, ApproveRejectUnitRequestDto request);

    CompanyResponseDto reviewUnit(Long companyId, Long unitId, Long reviewedById, ApproveRejectUnitRequestDto request);
}
