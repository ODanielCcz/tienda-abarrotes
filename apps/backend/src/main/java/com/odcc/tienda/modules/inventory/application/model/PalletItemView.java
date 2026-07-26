package com.odcc.tienda.modules.inventory.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PalletItemView(
    UUID palletItemId,
    UUID productPresentationId,
    String sku,
    String presentationName,
    UUID lotId,
    String lotNumber,
    BigDecimal quantity
) {
}
