package com.account.service.company;

import com.account.dto.company.CompanyRequestDto;
import com.account.dto.company.CompanyResponseDto;

public interface CompanyService {


    CompanyResponseDto createCompanyFromLead(CompanyRequestDto requestDto);
}
