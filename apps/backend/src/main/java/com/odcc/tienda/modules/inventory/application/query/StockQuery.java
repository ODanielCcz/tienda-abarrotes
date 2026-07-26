package com.odcc.tienda.modules.inventory.application.query;

import java.util.UUID;

public record StockQuery(UUID warehouseId, UUID productPresentationId) {
}
