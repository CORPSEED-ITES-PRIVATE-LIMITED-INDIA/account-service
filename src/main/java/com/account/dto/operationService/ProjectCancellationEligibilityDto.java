package com.account.dto.operationService;

import lombok.Data;

@Data
public class ProjectCancellationEligibilityDto {

    private Long projectId;
    private String projectNo;
    private String projectStatus;

    private int milestoneCompletionPercentage;
    private long totalMilestones;
    private long completedMilestones;

    private boolean certificationMilestonePresent;
    private boolean certificationCompleted;

    private String unbilledNumber;

    private boolean cancellationAllowed;
    private String blockReason;
}