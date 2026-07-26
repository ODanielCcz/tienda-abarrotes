package com.odcc.tienda.modules.inventory.application.query;

import java.util.UUID;

public record StockMovementQuery(UUID warehouseId, String movementType, String status) {
}
