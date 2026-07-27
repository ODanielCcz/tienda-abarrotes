package com.odcc.tienda.modules.inventory.application.command;

import java.util.UUID;

public record ReleaseReservationCommand(
    UUID reservationId,
    UUID releasedBy
) {
}
