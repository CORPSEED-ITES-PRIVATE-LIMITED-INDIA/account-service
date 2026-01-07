package com.account.service;

import com.account.dto.estimate.EstimateRequestDto;
import com.account.dto.estimate.EstimateResponseDto;

import java.util.List;
import java.util.Map;

public interface EstimateService {

    EstimateResponseDto createEstimate(EstimateRequestDto requestDto);

    EstimateResponseDto getEstimateById(Long id);

    List<EstimateResponseDto> getEstimatesByCompanyId(Long companyId, int page, int size);

    List<EstimateResponseDto> getAllEstimates(int page, int size);

    List<Map<String, Object>> searchEstimates(String query, int page, int size);

    Map<String, Object> getEstimateForView(Long id);
}