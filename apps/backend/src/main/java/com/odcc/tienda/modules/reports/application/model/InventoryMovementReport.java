package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryMovementReport(
    UUID warehouseId,
    String warehouseName,
    String movementType,
    long movementCount,
    BigDecimal totalQuantity
) {
}
