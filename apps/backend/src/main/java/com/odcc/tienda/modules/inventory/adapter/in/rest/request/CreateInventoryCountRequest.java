package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateInventoryCountRequest(
    @NotNull UUID warehouseId,
    @Valid @NotEmpty List<InventoryCountItemRequest> items
) {
}
