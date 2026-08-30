package com.odcc.tienda.modules.catalog.adapter.in.rest.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogPriceResponse(
    UUID priceId,
    UUID productPresentationId,
    UUID branchId,
    BigDecimal amount,
    String currencyCode
) {
}
