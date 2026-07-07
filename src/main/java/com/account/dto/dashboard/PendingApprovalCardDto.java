package com.account.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingApprovalCardDto {

    private Long count;
    private Long urgentTodayCount;
    private String label; // 6 urgent today

    public void normalize() {
        this.count = count == null ? 0L : count;
        this.urgentTodayCount = urgentTodayCount == null ? 0L : urgentTodayCount;
        this.label = this.urgentTodayCount + " urgent today";
    }
}