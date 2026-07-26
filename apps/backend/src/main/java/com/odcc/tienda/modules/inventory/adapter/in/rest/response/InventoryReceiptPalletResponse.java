package com.odcc.tienda.modules.inventory.adapter.in.rest.response;

import java.util.List;
import java.util.UUID;

public record InventoryReceiptPalletResponse(
    UUID palletId,
    String palletCode,
    String externalPalletCode,
    String status,
    List<InventoryReceiptItemResponse> items
) {
}
