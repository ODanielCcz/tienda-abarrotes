package com.odcc.tienda.modules.inventory.application.model;

import java.util.List;
import java.util.UUID;

public record InventoryReceiptPallet(
    UUID palletId,
    String palletCode,
    String externalPalletCode,
    String status,
    List<InventoryReceiptItem> items
) {
}
