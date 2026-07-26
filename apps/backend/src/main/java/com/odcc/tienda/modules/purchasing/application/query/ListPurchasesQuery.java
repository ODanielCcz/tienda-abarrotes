package com.odcc.tienda.modules.purchasing.application.query;

import java.util.UUID;

public record ListPurchasesQuery(UUID supplierId, UUID warehouseId, String status) {
}
