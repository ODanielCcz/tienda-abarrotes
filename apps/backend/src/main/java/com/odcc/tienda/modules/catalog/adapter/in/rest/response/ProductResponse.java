package com.odcc.tienda.modules.catalog.adapter.in.rest.response;

import com.odcc.tienda.modules.catalog.domain.model.ProductStatus;
import com.odcc.tienda.modules.catalog.domain.model.ProductType;

import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    UUID categoryId,
    UUID brandId,
    String name,
    String description,
    ProductType productType,
    boolean tracksInventory,
    boolean tracksLots,
    boolean tracksExpiration,
    ProductStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
