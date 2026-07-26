package com.odcc.tienda.modules.purchasing.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Purchase(
    UUID purchaseId,
    UUID branchId,
    UUID warehouseId,
    UUID supplierId,
    String supplierDocument,
    String status,
    String paymentStatus,
    String currencyCode,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal total,
    UUID idempotencyKey,
    Instant purchasedAt,
    Instant confirmedAt,
    Instant createdAt,
    List<PurchaseItem> items
) {
}
