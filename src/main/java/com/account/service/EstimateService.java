package com.account.service;

import com.account.domain.EstimateStatus;
import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.estimate.EstimateResponseDto;

import java.util.List;

public interface EstimateService {
    EstimateResponseDto createEstimate(EstimateCreationRequestDto requestDto);
    EstimateResponseDto getEstimateById(Long estimateId, Long requestingUserId);
}