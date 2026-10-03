package com.account.dto.unbilled;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnbilledProjectCompletionDto {

    private boolean projectFound;
    private Long projectId;
    private String projectNo;
    private String projectStatus;

    private int milestoneCompletionPercentage;
    private long totalMilestones;
    private long completedMilestones;

    private boolean certificationMilestonePresent;
    private boolean certificationCompleted;

    private boolean cancellationAllowed;
    private String blockReason;
}