package com.odcc.tienda.modules.sales.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SalesOrder(
    UUID salesOrderId,
    String orderNumber,
    UUID branchId,
    UUID warehouseId,
    UUID customerId,
    UUID deviceId,
    String channel,
    String status,
    String paymentStatus,
    String currencyCode,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal total,
    UUID idempotencyKey,
    Instant createdAt,
    Instant confirmedAt,
    Instant cancelledAt,
    List<SalesOrderItem> items
) {
}
