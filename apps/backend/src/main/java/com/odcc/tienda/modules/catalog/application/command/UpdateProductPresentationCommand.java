package com.odcc.tienda.modules.catalog.application.command;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductPresentationCommand(
    UUID presentationId,
    UUID unitId,
    UUID taxId,
    String sku,
    String name,
    BigDecimal conversionFactor,
    BigDecimal netContent,
    BigDecimal minimumStock
) {
}
