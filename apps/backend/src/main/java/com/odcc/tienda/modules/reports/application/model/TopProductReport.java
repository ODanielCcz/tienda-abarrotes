package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record TopProductReport(
    UUID productPresentationId,
    String sku,
    String productName,
    BigDecimal quantitySold,
    BigDecimal grossAmount
) {
}
