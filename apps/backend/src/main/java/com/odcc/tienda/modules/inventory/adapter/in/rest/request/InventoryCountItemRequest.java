package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryCountItemRequest(
    @NotNull UUID productPresentationId,
    UUID lotId,
    @NotNull @PositiveOrZero BigDecimal countedQuantity
) {
}
