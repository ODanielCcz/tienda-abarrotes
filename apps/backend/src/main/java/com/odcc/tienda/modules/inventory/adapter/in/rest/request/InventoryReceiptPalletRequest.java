package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InventoryReceiptPalletRequest(
    String externalPalletCode,
    @Valid @NotEmpty(message = "El pallet debe contener al menos un item") @Size(max = 500) List<InventoryReceiptItemRequest> items
) {
}
