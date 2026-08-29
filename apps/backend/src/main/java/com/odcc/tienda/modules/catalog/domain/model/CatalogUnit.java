package com.odcc.tienda.modules.catalog.domain.model;

import java.util.UUID;

public record CatalogUnit(
    UUID id,
    String code,
    String name,
    String symbol,
    int quantityScale
) {
}
