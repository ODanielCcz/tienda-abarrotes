package com.odcc.tienda.modules.inventory.application.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReceipt(
    UUID receiptId,
    UUID warehouseId,
    UUID supplierId,
    String status,
    Instant receivedAt,
    List<InventoryReceiptItem> items,
    List<InventoryReceiptPallet> pallets
) {
}
