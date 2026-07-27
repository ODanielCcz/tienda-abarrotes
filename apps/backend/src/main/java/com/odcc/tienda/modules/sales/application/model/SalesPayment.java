package com.odcc.tienda.modules.sales.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SalesPayment(
    UUID paymentId,
    UUID salesOrderId,
    UUID cashSessionId,
    String paymentMethod,
    String status,
    BigDecimal amount,
    String currencyCode,
    String reference,
    UUID idempotencyKey,
    Instant paidAt,
    Instant createdAt
) {
}
