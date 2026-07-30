package com.odcc.tienda.modules.sales.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SalesCart(
    UUID cartId,
    UUID customerId,
    UUID branchId,
    UUID deviceId,
    String status,
    String currencyCode,
    Instant createdAt,
    Instant updatedAt,
    Instant expiresAt,
    List<SalesCartItem> items
) {
    public record SalesCartItem(
        UUID cartItemId,
        UUID productPresentationId,
        BigDecimal quantity,
        BigDecimal unitPriceSnapshot
    ) {
    }
}
