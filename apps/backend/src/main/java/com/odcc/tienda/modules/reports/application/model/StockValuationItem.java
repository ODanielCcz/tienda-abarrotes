package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record StockValuationItem(
    UUID warehouseId,
    String warehouseName,
    UUID productPresentationId,
    String sku,
    String productName,
    String presentationName,
    BigDecimal onHandQuantity,
    BigDecimal availableQuantity,
    BigDecimal averageUnitCost,
    BigDecimal stockValue
) {
}
