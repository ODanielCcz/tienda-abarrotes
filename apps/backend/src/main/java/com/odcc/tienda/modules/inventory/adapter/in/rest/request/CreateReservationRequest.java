package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
    UUID customerId,
    @NotNull String sourceType,
    @NotNull UUID sourceId,
    @NotNull UUID idempotencyKey,
    @NotNull @Future Instant expiresAt,
    @Valid @NotEmpty List<ReservationItemRequest> items
) {
}
