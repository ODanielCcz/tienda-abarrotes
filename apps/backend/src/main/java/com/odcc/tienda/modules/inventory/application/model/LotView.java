package com.odcc.tienda.modules.inventory.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record LotView(
    UUID lotId,
    UUID productPresentationId,
    UUID supplierId,
    String lotNumber,
    LocalDate manufacturedAt,
    LocalDate expiresAt,
    String status,
    UUID warehouseId,
    BigDecimal onHandQuantity,
    BigDecimal availableQuantity,
    Instant createdAt
) {
}
