package com.odcc.tienda.modules.purchasing.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseItem(
    UUID purchaseItemId,
    UUID purchaseId,
    UUID productPresentationId,
    UUID lotId,
    String productNameSnapshot,
    String skuSnapshot,
    BigDecimal quantity,
    BigDecimal receivedQuantity,
    BigDecimal unitCost,
    BigDecimal discountAmount,
    BigDecimal taxAmount,
    BigDecimal lineTotal
) {
}
