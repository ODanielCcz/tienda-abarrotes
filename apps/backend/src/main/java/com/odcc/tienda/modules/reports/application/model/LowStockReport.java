package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record LowStockReport(
    UUID warehouseId,
    String warehouseName,
    UUID productPresentationId,
    String sku,
    String presentationName,
    BigDecimal availableQuantity,
    BigDecimal minimumStock
) {
}
