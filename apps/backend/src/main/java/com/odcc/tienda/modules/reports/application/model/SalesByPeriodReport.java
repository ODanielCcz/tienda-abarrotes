package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesByPeriodReport(
    LocalDate periodStart,
    LocalDate periodEnd,
    long ticketCount,
    BigDecimal grossSubtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal grossSales,
    BigDecimal returnsAmount,
    BigDecimal netSales
) {
}
