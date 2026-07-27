package com.odcc.tienda.modules.inventory.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryCountView(
    UUID inventoryCountId,
    UUID warehouseId,
    String status,
    UUID startedBy,
    UUID confirmedBy,
    Instant startedAt,
    Instant confirmedAt,
    List<InventoryCountItemView> items
) {
}
