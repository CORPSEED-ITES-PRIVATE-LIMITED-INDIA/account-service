package com.account.service;

import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.estimate.EstimateResponseDto;
import java.util.List;



public interface EstimateService {
    EstimateResponseDto createEstimate(EstimateCreationRequestDto requestDto);
    EstimateResponseDto getEstimateById(Long estimateId, Long requestingUserId);

    List<EstimateResponseDto> getEstimatesByLeadId(Long leadId);

    List<EstimateResponseDto> getEstimatesByCompanyId(Long companyId);

    List<EstimateResponseDto> getAllEstimates(Long requestingUserId, int i, int size);

    long getEstimatesCount(Long requestingUserId);
}

