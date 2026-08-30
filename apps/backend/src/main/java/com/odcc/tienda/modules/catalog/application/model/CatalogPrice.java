package com.odcc.tienda.modules.catalog.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogPrice(
    UUID priceId,
    UUID productPresentationId,
    UUID branchId,
    BigDecimal amount,
    String currencyCode
) {
}
