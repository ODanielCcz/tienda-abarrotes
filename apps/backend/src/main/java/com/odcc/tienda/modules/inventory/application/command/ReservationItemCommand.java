package com.odcc.tienda.modules.inventory.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationItemCommand(
    UUID warehouseId,
    UUID productPresentationId,
    UUID lotId,
    BigDecimal quantity
) {
}
