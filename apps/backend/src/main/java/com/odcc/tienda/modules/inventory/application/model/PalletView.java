package com.odcc.tienda.modules.inventory.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PalletView(
    UUID palletId,
    UUID warehouseId,
    UUID stockMovementId,
    String palletCode,
    String externalPalletCode,
    String status,
    Instant receivedAt,
    List<PalletItemView> items
) {
}
