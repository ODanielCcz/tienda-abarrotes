package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateInventoryReceiptRequest(
    @NotNull(message = "El almacen es obligatorio") UUID warehouseId,
    UUID supplierId,
    UUID idempotencyKey,
    String reason,
    @Valid List<InventoryReceiptItemRequest> items,
    @Valid List<InventoryReceiptPalletRequest> pallets
) {
}
