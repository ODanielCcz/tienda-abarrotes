package com.odcc.tienda.modules.sales.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesReturnItem(
    UUID returnItemId,
    UUID returnId,
    UUID salesOrderItemId,
    UUID productPresentationId,
    UUID lotId,
    String productNameSnapshot,
    String skuSnapshot,
    BigDecimal quantity,
    BigDecimal amount
) {
}