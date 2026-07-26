package com.odcc.tienda.modules.catalog.application.command;

import com.odcc.tienda.modules.catalog.domain.model.ProductType;

import java.util.UUID;

public record UpdateProductCommand(
    UUID productId,
    UUID categoryId,
    UUID brandId,
    String name,
    String description,
    ProductType productType,
    boolean tracksInventory,
    boolean tracksLots,
    boolean tracksExpiration
) {
}
