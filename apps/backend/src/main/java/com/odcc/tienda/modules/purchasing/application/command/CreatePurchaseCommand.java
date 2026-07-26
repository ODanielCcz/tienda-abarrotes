package com.odcc.tienda.modules.purchasing.application.command;

import java.util.List;
import java.util.UUID;

public record CreatePurchaseCommand(
    UUID warehouseId,
    UUID supplierId,
    String supplierDocument,
    String currencyCode,
    UUID idempotencyKey,
    List<CreatePurchaseItemCommand> items
) {
}
