package com.odcc.tienda.modules.inventory.application.query;

import java.time.LocalDate;
import java.util.UUID;

public record LotQuery(
    UUID warehouseId,
    UUID productPresentationId,
    String status,
    LocalDate expiresBefore,
    LocalDate expiresAfter
) {
}
