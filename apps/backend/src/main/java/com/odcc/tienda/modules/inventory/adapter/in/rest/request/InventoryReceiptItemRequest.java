package com.odcc.tienda.modules.inventory.adapter.in.rest.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryReceiptItemRequest(
    @NotNull(message = "La presentacion del producto es obligatoria") UUID productPresentationId,
    String lotNumber,
    LocalDate manufacturedAt,
    LocalDate expiresAt,
    @NotNull(message = "La cantidad es obligatoria") @DecimalMin(value = "0.001", message = "La cantidad debe ser mayor a cero") BigDecimal quantity,
    @DecimalMin(value = "0.0000", message = "El costo unitario no puede ser negativo") BigDecimal unitCost
) {
}
