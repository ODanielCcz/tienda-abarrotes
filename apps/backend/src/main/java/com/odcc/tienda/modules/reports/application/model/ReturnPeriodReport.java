package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReturnPeriodReport(
    LocalDate periodStart,
    LocalDate periodEnd,
    long returnCount,
    BigDecimal returnedQuantity,
    BigDecimal returnedAmount
) {
}
