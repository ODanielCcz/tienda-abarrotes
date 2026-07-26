package com.odcc.tienda.modules.inventory.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockBalanceView(
    UUID stockBalanceId,
    UUID warehouseId,
    UUID productPresentationId,
    String sku,
    String presentationName,
    String productName,
    BigDecimal onHandQuantity,
    BigDecimal reservedQuantity,
    BigDecimal allocatedQuantity,
    BigDecimal availableQuantity,
    BigDecimal averageUnitCost,
    Instant updatedAt
) {
}
