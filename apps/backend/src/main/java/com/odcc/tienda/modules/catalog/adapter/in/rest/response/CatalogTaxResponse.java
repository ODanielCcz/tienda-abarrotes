package com.odcc.tienda.modules.catalog.adapter.in.rest.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogTaxResponse(
    UUID id,
    String code,
    String name,
    BigDecimal rate
) {
}
