package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryAdjustmentItemRequest(
    @NotNull UUID productPresentationId,
    UUID lotId,
    @NotNull String direction,
    @NotNull @Positive BigDecimal quantity,
    BigDecimal unitCost
) {
}
