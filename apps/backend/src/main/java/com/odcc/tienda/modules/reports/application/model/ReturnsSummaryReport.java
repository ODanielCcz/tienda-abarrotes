package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.List;

public record ReturnsSummaryReport(
    long returnCount,
    BigDecimal returnedQuantity,
    BigDecimal returnedAmount,
    List<ReturnPeriodReport> periods
) {
}
