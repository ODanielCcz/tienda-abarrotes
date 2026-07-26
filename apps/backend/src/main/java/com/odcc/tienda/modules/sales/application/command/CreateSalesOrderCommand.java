package com.odcc.tienda.modules.sales.application.command;

import java.util.List;
import java.util.UUID;

public record CreateSalesOrderCommand(
    UUID warehouseId,
    UUID customerId,
    UUID deviceId,
    String channel,
    String currencyCode,
    UUID idempotencyKey,
    List<CreateSalesOrderItemCommand> items
) {
}
