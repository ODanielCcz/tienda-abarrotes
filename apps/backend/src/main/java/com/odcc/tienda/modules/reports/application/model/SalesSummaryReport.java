package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;

public record SalesSummaryReport(
    long ticketCount,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal total,
    BigDecimal averageTicket
) {
}
