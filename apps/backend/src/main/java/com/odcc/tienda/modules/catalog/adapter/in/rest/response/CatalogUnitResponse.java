package com.odcc.tienda.modules.catalog.adapter.in.rest.response;

import java.util.UUID;

public record CatalogUnitResponse(
    UUID id,
    String code,
    String name,
    String symbol,
    int quantityScale
) {
}
