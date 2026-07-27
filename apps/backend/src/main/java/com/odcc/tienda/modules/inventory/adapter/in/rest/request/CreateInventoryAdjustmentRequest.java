package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateInventoryAdjustmentRequest(
    @NotNull UUID warehouseId,
    String reason,
    @Valid @NotEmpty List<InventoryAdjustmentItemRequest> items
) {
}
