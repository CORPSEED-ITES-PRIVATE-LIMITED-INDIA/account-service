package com.account.service;

import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.estimate.EstimateResponseDto;


public interface EstimateService {
    EstimateResponseDto createEstimate(EstimateCreationRequestDto requestDto);
    EstimateResponseDto getEstimateById(Long estimateId, Long requestingUserId);
}