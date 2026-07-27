package com.odcc.tienda.modules.inventory.application.command;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateReservationCommand(
    UUID customerId,
    String sourceType,
    UUID sourceId,
    UUID idempotencyKey,
    Instant expiresAt,
    UUID createdBy,
    List<ReservationItemCommand> items
) {
}
