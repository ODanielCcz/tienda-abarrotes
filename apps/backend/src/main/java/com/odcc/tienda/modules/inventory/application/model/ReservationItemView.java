package com.odcc.tienda.modules.inventory.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationItemView(
    UUID reservationItemId,
    UUID warehouseId,
    UUID productPresentationId,
    UUID lotId,
    BigDecimal quantity
) {
}
