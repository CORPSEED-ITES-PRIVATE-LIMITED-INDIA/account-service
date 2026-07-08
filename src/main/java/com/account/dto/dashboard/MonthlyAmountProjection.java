package com.account.dto.dashboard;

import java.math.BigDecimal;

public interface MonthlyAmountProjection {

    String getMonthKey();

    BigDecimal getAmount();
}