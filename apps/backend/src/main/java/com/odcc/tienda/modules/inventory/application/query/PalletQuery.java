package com.odcc.tienda.modules.inventory.application.query;

import java.util.UUID;

public record PalletQuery(UUID warehouseId, String status) {
}
