package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;

public record GrossMarginReport(
    BigDecimal grossRevenueExcludingTax,
    BigDecimal returnedRevenueExcludingTax,
    BigDecimal netRevenueExcludingTax,
    BigDecimal grossCost,
    BigDecimal returnedCost,
    BigDecimal netCost,
    BigDecimal grossProfit,
    BigDecimal grossMarginPercent
) {
}
