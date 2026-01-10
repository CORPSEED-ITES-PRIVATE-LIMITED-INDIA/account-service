package com.account.service;

import com.account.dto.OrganizationRequestDto;
import com.account.dto.OrganizationResponseDto;

public interface OrganizationService {

	OrganizationResponseDto saveOrganization(OrganizationRequestDto requestDto, Long currentUserId);

	OrganizationResponseDto getOrganization();
}