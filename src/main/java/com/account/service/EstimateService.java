package com.account.service;

import com.account.dto.EstimateCreationRequestDto;
import com.account.dto.dashboard.EstimateDashboardFilterRequest;
import com.account.dto.dashboard.EstimateDashboardResponse;
import com.account.dto.estimate.EstimateResponseDto;
import com.account.dto.estimate.EstimateSearchRequest;
import com.account.dto.estimate.EstimateSearchRequestDto;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;



public interface EstimateService {
    EstimateResponseDto createEstimate(EstimateCreationRequestDto requestDto);
    EstimateResponseDto getEstimateById(Long estimateId, Long requestingUserId);

    List<EstimateResponseDto> getEstimatesByLeadId(Long leadId);

    List<EstimateResponseDto> getEstimatesByCompanyId(Long companyId);

    long getEstimatesCount(Long requestingUserId);

    EstimateResponseDto sendEstimateToClient(Long estimateId, Long requestingUserId);

    List<EstimateResponseDto> getAllEstimates(
            Long requestingUserId,
            String search,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size
    );

    EstimateDashboardResponse getEstimateDashboard(
            EstimateDashboardFilterRequest request
    );

    Page<EstimateResponseDto> estimateReport(
            EstimateSearchRequest request
    );


    Page<EstimateResponseDto> searchEstimates(EstimateSearchRequestDto request, Long userId);
}




