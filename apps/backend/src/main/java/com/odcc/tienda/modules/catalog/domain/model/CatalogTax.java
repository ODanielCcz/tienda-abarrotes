package com.odcc.tienda.modules.catalog.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogTax(
    UUID id,
    String code,
    String name,
    BigDecimal rate
) {
}
