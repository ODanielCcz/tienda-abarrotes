package com.odcc.tienda.modules.inventory.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationView(
    UUID reservationId,
    UUID branchId,
    UUID customerId,
    String sourceType,
    UUID sourceId,
    String status,
    UUID idempotencyKey,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    List<ReservationItemView> items
) {
}
