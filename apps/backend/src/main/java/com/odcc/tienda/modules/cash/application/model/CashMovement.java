package com.odcc.tienda.modules.cash.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovement(
    UUID cashMovementId,
    UUID cashSessionId,
    String movementType,
    String direction,
    BigDecimal amount,
    UUID paymentId,
    String reference,
    String reason,
    UUID createdBy,
    Instant createdAt
) {
}
