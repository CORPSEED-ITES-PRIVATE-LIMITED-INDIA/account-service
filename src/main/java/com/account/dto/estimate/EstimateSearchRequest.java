package com.account.dto.estimate;

import com.account.dto.dashboard.EstimateDashboardFilterRequest;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstimateSearchRequest extends EstimateDashboardFilterRequest {

    private int page = 0;
    private int size = 20;

    private String sortBy = "createdAt";
    private String sortDirection = "DESC";
}
