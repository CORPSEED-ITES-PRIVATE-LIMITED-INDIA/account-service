package com.account.service.company;

import com.account.dto.BasicCompanyRequestDto;
import com.account.dto.company.request.ApproveRejectUnitRequestDto;
import com.account.dto.company.request.BasicUnitCreateRequest;
import com.account.dto.company.request.CompanyRequestDto;
import com.account.dto.company.response.CompanyResponseDto;

public interface CompanyService {


    CompanyResponseDto basicCreateCompany(BasicCompanyRequestDto quickRequest);

    CompanyResponseDto addBasicUnitToCompany(Long companyId, BasicUnitCreateRequest request, Long updatedById);

    CompanyResponseDto updateFullCompanyDetails(Long companyId, CompanyRequestDto dto, Long updatedById);

    CompanyResponseDto reviewUnit(Long companyId, Long unitId, Long reviewedById, ApproveRejectUnitRequestDto request);
}
