package com.account.repository.projection;

import java.math.BigDecimal;

public interface TdsCollectionSummaryProjection {

    BigDecimal getTotalTdsAmount();

    BigDecimal getPendingAmount();

    BigDecimal getClaimedAmount();

    Long getTotalCount();

    Long getPendingCount();

    Long getClaimedCount();
}