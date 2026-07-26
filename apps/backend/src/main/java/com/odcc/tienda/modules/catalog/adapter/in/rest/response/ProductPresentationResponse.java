package com.odcc.tienda.modules.catalog.adapter.in.rest.response;

import com.odcc.tienda.modules.catalog.domain.model.ProductPresentationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductPresentationResponse(
    UUID id,
    UUID productId,
    UUID unitId,
    UUID taxId,
    String sku,
    String name,
    BigDecimal conversionFactor,
    BigDecimal netContent,
    BigDecimal minimumStock,
    ProductPresentationStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
