package com.odcc.tienda.modules.sales.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItem(
    UUID salesOrderItemId,
    UUID salesOrderId,
    UUID productPresentationId,
    UUID lotId,
    String productNameSnapshot,
    String skuSnapshot,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal unitCost,
    BigDecimal discountAmount,
    BigDecimal taxRate,
    BigDecimal taxAmount,
    BigDecimal lineTotal
) {
}
