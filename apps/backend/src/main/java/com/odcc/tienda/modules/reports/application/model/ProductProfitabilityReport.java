package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductProfitabilityReport(
    UUID productPresentationId,
    String sku,
    String productName,
    BigDecimal quantitySold,
    BigDecimal quantityReturned,
    BigDecimal netQuantity,
    BigDecimal netRevenueExcludingTax,
    BigDecimal netCost,
    BigDecimal grossProfit,
    BigDecimal grossMarginPercent
) {
}
