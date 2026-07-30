package com.odcc.tienda.modules.sales.application.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UpsertSalesCartCommand(
    UUID cartId,
    UUID customerId,
    UUID branchId,
    UUID deviceId,
    String currencyCode,
    Instant expiresAt,
    List<Item> items
) {
    public record Item(
        UUID productPresentationId,
        BigDecimal quantity,
        BigDecimal unitPriceSnapshot
    ) {
    }
}
