package com.odcc.tienda.modules.sales.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SalesReturn(
    UUID returnId,
    UUID salesOrderId,
    String status,
    String reason,
    BigDecimal total,
    UUID createdBy,
    Instant createdAt,
    Instant confirmedAt,
    List<SalesReturnItem> items
) {
}