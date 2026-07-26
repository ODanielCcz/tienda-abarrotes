package com.odcc.tienda.modules.inventory.adapter.in.rest.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InventoryReceiptResponse(
    UUID receiptId,
    UUID warehouseId,
    UUID supplierId,
    String status,
    Instant receivedAt,
    List<InventoryReceiptItemResponse> items,
    List<InventoryReceiptPalletResponse> pallets
) {
}
