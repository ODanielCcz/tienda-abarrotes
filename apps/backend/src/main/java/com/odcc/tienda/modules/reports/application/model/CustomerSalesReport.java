package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerSalesReport(
    UUID customerId,
    String customerCode,
    String displayName,
    long ticketCount,
    BigDecimal total
) {
}
