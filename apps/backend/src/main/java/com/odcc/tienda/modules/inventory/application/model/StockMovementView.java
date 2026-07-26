package com.odcc.tienda.modules.inventory.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockMovementView(
    UUID stockMovementId,
    UUID branchId,
    UUID warehouseId,
    String movementType,
    String status,
    String sourceType,
    UUID sourceId,
    String reason,
    UUID idempotencyKey,
    Instant createdAt,
    Instant confirmedAt,
    List<StockMovementItemView> items
) {
}
