package com.odcc.tienda.modules.reports.application.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpiringProductReport(
    UUID lotId,
    String lotNumber,
    UUID warehouseId,
    String warehouseName,
    UUID productPresentationId,
    String sku,
    String productName,
    String presentationName,
    LocalDate expiresAt,
    int daysRemaining,
    BigDecimal onHandQuantity,
    BigDecimal availableQuantity,
    BigDecimal averageUnitCost,
    BigDecimal estimatedValue
) {
}
