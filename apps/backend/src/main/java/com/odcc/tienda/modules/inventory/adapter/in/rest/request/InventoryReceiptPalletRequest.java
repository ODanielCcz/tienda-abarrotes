package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InventoryReceiptPalletRequest(
    String externalPalletCode,
    @Valid @NotEmpty(message = "El pallet debe contener al menos un item") List<InventoryReceiptItemRequest> items
) {
}
